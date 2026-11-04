package addresses

import (
	"github.com/btcsuite/btcd/btcec/v2"
	"github.com/btcsuite/btcd/btcec/v2/ecdsa"
	"github.com/btcsuite/btcd/btcutil"
	"github.com/btcsuite/btcd/txscript"
	"github.com/btcsuite/btcd/wire"
	"github.com/go-errors/errors"

	"github.com/muun/libwallet/base/domain/cryptography/hd_keys"
	"github.com/muun/libwallet/platform/preconditions"
)

// preTaprootToClient carries the participant keys and the spending logic
// shared by the pre-taproot to_client outputs: V7 and V8 commit to the same
// witness script, so everything except the address encoding is common to
// them.
type preTaprootToClient struct {
	UserPubKey            *btcec.PublicKey
	CosignerPubKey        *btcec.PublicKey
	LightningServerPubKey *btcec.PublicKey
	blocksForExpiration   int64
	path                  string
}

// newPreTaprootToClient builds a preTaprootToClient over participant keys
// already derived at derivationPath, which the signers later derive the
// private keys to, waiting blocksForExpiration on the non-collaborative path.
// Panics if any key is nil or derivationPath is empty.
func newPreTaprootToClient(
	userPubKey, cosignerPubKey, lightningServerPubKey *btcec.PublicKey,
	derivationPath string,
	blocksForExpiration int64,
) preTaprootToClient {
	return preTaprootToClient{
		UserPubKey:            preconditions.CheckNotNil(userPubKey),
		CosignerPubKey:        preconditions.CheckNotNil(cosignerPubKey),
		LightningServerPubKey: preconditions.CheckNotNil(lightningServerPubKey),
		blocksForExpiration:   blocksForExpiration,
		path:                  preconditions.CheckNotEmpty(derivationPath),
	}
}

// witnessScript builds the witness script committed by this output.
//
// Two spending paths:
//  1. collaborative path: user + cosigner + lightningServer
//  2. non-collaborative path: user + cosigner + a RELATIVE timelock of blocksForExpiration blocks
func (o *preTaprootToClient) witnessScript() ([]byte, error) {
	builder := txscript.NewScriptBuilder()

	// User key, required in BOTH paths.
	builder.AddData(o.UserPubKey.SerializeCompressed()).
		AddOp(txscript.OP_CHECKSIGVERIFY)

	// Cosigner key, required in BOTH paths.
	builder.AddData(o.CosignerPubKey.SerializeCompressed()).
		AddOp(txscript.OP_CHECKSIGVERIFY)

	// The lightning server's signature doubles as the branch selector:
	//   - collaborative path: a valid signature leaves true and skips the NOTIF branch.
	//   - non-collaborative path: an EMPTY signature makes the timelock branch run.
	builder.AddData(o.LightningServerPubKey.SerializeCompressed()).
		AddOp(txscript.OP_CHECKSIG).
		AddOp(txscript.OP_IFDUP).
		AddOp(txscript.OP_NOTIF).
		// CHECKSEQUENCEVERIFY leaves its argument (blocksForExpiration, truthy) on the stack, so
		// no OP_DROP or explicit truthy push is needed.
		AddInt64(o.blocksForExpiration).
		AddOp(txscript.OP_CHECKSEQUENCEVERIFY).
		AddOp(txscript.OP_ENDIF)

	return builder.Script()
}

// ComputeUserPartialSignature computes the user's signature for the input at
// index. The signature commits to the witness script as a whole, so it serves
// either spending path.
func (o *preTaprootToClient) ComputeUserPartialSignature(
	index int,
	tx *wire.MsgTx,
	sigHashes *txscript.TxSigHashes,
	amount btcutil.Amount,
	userPrivKey *hd_keys.HDPrivateKey,
) ([]byte, error) {
	userECPrivKey, err := deriveSignerECKey(userPrivKey, o.path, o.UserPubKey)
	if err != nil {
		return nil, errors.Errorf("user key: %w", err)
	}
	return o.computePartialSignature(index, tx, sigHashes, amount, userECPrivKey)
}

// ComputeCosignerPartialSignature computes the cosigner's signature for the
// input at index. The signature commits to the witness script as a whole, so
// it serves either spending path.
func (o *preTaprootToClient) ComputeCosignerPartialSignature(
	index int,
	tx *wire.MsgTx,
	sigHashes *txscript.TxSigHashes,
	amount btcutil.Amount,
	cosignerPrivKey *hd_keys.HDPrivateKey,
) ([]byte, error) {
	cosignerECPrivKey, err := deriveSignerECKey(cosignerPrivKey, o.path, o.CosignerPubKey)
	if err != nil {
		return nil, errors.Errorf("cosigner key: %w", err)
	}
	return o.computePartialSignature(index, tx, sigHashes, amount, cosignerECPrivKey)
}

// ComputeLightningServerPartialSignature computes the lightning server's
// signature for the input at index. The signature only serves the
// collaborative path: the non-collaborative path doesn't take a lightning
// server signature.
func (o *preTaprootToClient) ComputeLightningServerPartialSignature(
	index int,
	tx *wire.MsgTx,
	sigHashes *txscript.TxSigHashes,
	amount btcutil.Amount,
	lightningServerPrivKey *hd_keys.HDPrivateKey,
) ([]byte, error) {
	lightningServerECPrivKey, err := deriveSignerECKey(
		lightningServerPrivKey, o.path, o.LightningServerPubKey,
	)
	if err != nil {
		return nil, errors.Errorf("lightning server key: %w", err)
	}
	return o.computePartialSignature(index, tx, sigHashes, amount, lightningServerECPrivKey)
}

// computePartialSignature computes signerPrivKey's signature over the witness
// script for the input at index.
func (o *preTaprootToClient) computePartialSignature(
	index int,
	tx *wire.MsgTx,
	sigHashes *txscript.TxSigHashes,
	amount btcutil.Amount,
	signerPrivKey *btcec.PrivateKey,
) ([]byte, error) {
	witnessScript, err := o.witnessScript()
	if err != nil {
		return nil, err
	}

	return txscript.RawTxInWitnessSignature(
		tx,
		sigHashes,
		index,
		int64(amount),
		witnessScript,
		txscript.SigHashAll,
		signerPrivKey,
	)
}

// VerifyUserPartialSignature checks the user's signature for the input at
// index.
func (o *preTaprootToClient) VerifyUserPartialSignature(
	index int,
	tx *wire.MsgTx,
	sigHashes *txscript.TxSigHashes,
	amount btcutil.Amount,
	signature []byte,
) error {
	return o.verifyPartialSignature(index, tx, sigHashes, amount, o.UserPubKey, signature)
}

// VerifyCosignerPartialSignature checks the cosigner's signature for the
// input at index.
func (o *preTaprootToClient) VerifyCosignerPartialSignature(
	index int,
	tx *wire.MsgTx,
	sigHashes *txscript.TxSigHashes,
	amount btcutil.Amount,
	signature []byte,
) error {
	return o.verifyPartialSignature(index, tx, sigHashes, amount, o.CosignerPubKey, signature)
}

// VerifyLightningServerPartialSignature checks the lightning server's
// signature for the input at index.
func (o *preTaprootToClient) VerifyLightningServerPartialSignature(
	index int,
	tx *wire.MsgTx,
	sigHashes *txscript.TxSigHashes,
	amount btcutil.Amount,
	signature []byte,
) error {
	return o.verifyPartialSignature(
		index, tx, sigHashes, amount, o.LightningServerPubKey, signature,
	)
}

// verifyPartialSignature checks signature against signerPubKey over the
// witness script for the input at index.
func (o *preTaprootToClient) verifyPartialSignature(
	index int,
	tx *wire.MsgTx,
	sigHashes *txscript.TxSigHashes,
	amount btcutil.Amount,
	signerPubKey *btcec.PublicKey,
	signature []byte,
) error {
	if len(signature) == 0 {
		return errors.New("empty signature")
	}
	hashType := txscript.SigHashType(signature[len(signature)-1])
	if hashType != txscript.SigHashAll {
		return errors.Errorf("expected a SIGHASH_ALL signature, got sighash type 0x%x", hashType)
	}
	parsedSignature, err := ecdsa.ParseDERSignature(signature[:len(signature)-1])
	if err != nil {
		return errors.Errorf("parse signature: %w", err)
	}

	witnessScript, err := o.witnessScript()
	if err != nil {
		return err
	}
	sigHash, err := txscript.CalcWitnessSigHash(
		witnessScript, sigHashes, txscript.SigHashAll, tx, index, int64(amount),
	)
	if err != nil {
		return errors.Errorf("calc witness signature hash: %w", err)
	}

	if !parsedSignature.Verify(sigHash, signerPubKey) {
		return errors.New("invalid signature")
	}
	return nil
}

// AssembleCollaborativeWitness assembles the witness for the collaborative
// spend from every participant's signature.
func (o *preTaprootToClient) AssembleCollaborativeWitness(
	userSignature, cosignerSignature, lightningServerSignature []byte,
) (wire.TxWitness, error) {
	if len(userSignature) == 0 {
		return nil, errors.New("empty user signature")
	}
	if len(cosignerSignature) == 0 {
		return nil, errors.New("empty cosigner signature")
	}
	if len(lightningServerSignature) == 0 {
		return nil, errors.New("empty lightning server signature")
	}

	witnessScript, err := o.witnessScript()
	if err != nil {
		return nil, err
	}

	// Witness stack bottom -> top: lightningServerSig, cosignerSig, userSig, witnessScript.
	return wire.TxWitness{
		lightningServerSignature,
		cosignerSignature,
		userSignature,
		witnessScript,
	}, nil
}

// NonCollaborativeFullSignatures carries the two signatures that spend the
// non-collaborative (timelock branch) path.
type NonCollaborativeFullSignatures struct {
	UserSignature     []byte
	CosignerSignature []byte
}

// NewNonCollaborativeFullSignatures builds a NonCollaborativeFullSignatures from the
// user and cosigner signatures.
func NewNonCollaborativeFullSignatures(
	userSignature, cosignerSignature []byte,
) *NonCollaborativeFullSignatures {
	return &NonCollaborativeFullSignatures{
		UserSignature:     userSignature,
		CosignerSignature: cosignerSignature,
	}
}

// ComputeNonCollaborativeFullSignatures computes the user and cosigner signatures
// for the non-collaborative (timelock branch) spend of the input at index.
// Both signatures are computed locally.
// Panics if the input doesn't commit to a BIP68 relative timelock of at least
// blocksForExpiration.
func (o *preTaprootToClient) ComputeNonCollaborativeFullSignatures(
	index int,
	tx *wire.MsgTx,
	sigHashes *txscript.TxSigHashes,
	amount btcutil.Amount,
	userPrivKey, cosignerPrivKey *hd_keys.HDPrivateKey,
) (*NonCollaborativeFullSignatures, error) {
	checkRelativeTimelockSpend(tx, index, o.blocksForExpiration)

	userSignature, err := o.ComputeUserPartialSignature(index, tx, sigHashes, amount, userPrivKey)
	if err != nil {
		return nil, err
	}
	cosignerSignature, err := o.ComputeCosignerPartialSignature(
		index, tx, sigHashes, amount, cosignerPrivKey,
	)
	if err != nil {
		return nil, err
	}
	return NewNonCollaborativeFullSignatures(userSignature, cosignerSignature), nil
}

// AssembleNonCollaborativeWitness assembles the witness for the non-collaborative
// (timelock branch) spend from the user and cosigner signatures. The lack of
// lightningServer signature selects the branch.
func (o *preTaprootToClient) AssembleNonCollaborativeWitness(
	signatures *NonCollaborativeFullSignatures,
) (wire.TxWitness, error) {
	if len(signatures.UserSignature) == 0 {
		return nil, errors.New("empty user signature")
	}
	if len(signatures.CosignerSignature) == 0 {
		return nil, errors.New("empty cosigner signature")
	}

	witnessScript, err := o.witnessScript()
	if err != nil {
		return nil, err
	}

	// Witness stack bottom -> top: <empty>, cosignerSig, userSig, witnessScript.
	return wire.TxWitness{
		[]byte{},
		signatures.CosignerSignature,
		signatures.UserSignature,
		witnessScript,
	}, nil
}
