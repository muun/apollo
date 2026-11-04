package addresses

import (
	"github.com/btcsuite/btcd/btcec/v2"
	"github.com/btcsuite/btcd/btcec/v2/schnorr"
	"github.com/btcsuite/btcd/btcutil"
	"github.com/btcsuite/btcd/chaincfg"
	"github.com/btcsuite/btcd/txscript"
	"github.com/btcsuite/btcd/wire"
	"github.com/go-errors/errors"

	"github.com/muun/libwallet/base/domain/cryptography/hd_keys"
	"github.com/muun/libwallet/base/domain/cryptography/musig"
	"github.com/muun/libwallet/platform/preconditions"
)

// ToClientV9 is the user's balance output of the channel in its native
// taproot (P2TR) encoding. It has two spending paths:
//
//   - collaborative: taproot key spend path (MuSig2(user + cosigner + lightningServer))
//   - non-collaborative: the only script in the Tap Tree consisting of
//     user + cosigner + older(blocksForExpiration)
type ToClientV9 struct {
	UserPubKey            *btcec.PublicKey
	CosignerPubKey        *btcec.PublicKey
	LightningServerPubKey *btcec.PublicKey
	path                  string
	network               *chaincfg.Params
}

// The output's script tree has a single leaf.
const toClientV9NonCollaborativeLeafIndex = 0

// toClientV9NonCollaborativeDelay returns the relative timelock the
// non-collaborative leaf waits: to_client delay.
func toClientV9NonCollaborativeDelay() int64 {
	return delays.toClientDelay
}

// NewToClientV9 derives the participant keys to derivationPath and builds the
// output, whose non-collaborative path waits the spec's to_client delay.
// derivationPath must match m/schema:1'/recovery:1'/<change:0|external:1>/<index>.
func NewToClientV9(
	baseKeys *hd_keys.PublicKeyTriple,
	derivationPath string,
) (*ToClientV9, error) {
	keys, err := deriveParticipantECKeys(baseKeys, derivationPath)
	if err != nil {
		return nil, err
	}

	return &ToClientV9{
		UserPubKey:            keys.userPubKey,
		CosignerPubKey:        keys.cosignerPubKey,
		LightningServerPubKey: keys.lightningServerPubKey,
		path:                  derivationPath,
		network:               baseKeys.Network(),
	}, nil
}

// Address returns the P2TR address paying to this output.
func (o *ToClientV9) Address() (*WalletAddress, error) {
	tapScriptTree, err := o.tapScriptTree()
	if err != nil {
		return nil, err
	}
	internalKey, err := o.internalKey()
	if err != nil {
		return nil, err
	}

	scriptRootHash := tapScriptTree.RootNode.TapHash()
	outputKey := txscript.ComputeTaprootOutputKey(internalKey, scriptRootHash[:])

	taprootAddress, err := btcutil.NewAddressTaproot(
		schnorr.SerializePubKey(outputKey), o.network,
	)
	if err != nil {
		return nil, errors.Errorf("build taproot address: %w", err)
	}

	return New(V9, o.path, taprootAddress.EncodeAddress()), nil
}

// createInternalKeyV9 returns the taproot public key without the tweak.
func createInternalKeyV9(
	userPubKey, cosignerPubKey, lightningServerPubKey *btcec.PublicKey,
) (*btcec.PublicKey, error) {
	aggregate, err := musig.MuSig2CombineKeys(
		musig.Musig2v100,
		[]*btcec.PublicKey{userPubKey, cosignerPubKey, lightningServerPubKey},
		musig.NoopTweak(),
	)
	if err != nil {
		return nil, errors.Errorf("aggregate internal key: %w", err)
	}
	return aggregate.PreTweakedKey, nil
}

// createNonCollaborativeScriptV9 builds the single tapleaf script for the non-collaborative path:
//
//	<musig(user, cosigner)> OP_CHECKSIGVERIFY <blocksForExpiration> OP_CHECKSEQUENCEVERIFY
func createNonCollaborativeScriptV9(
	userPubKey, cosignerPubKey *btcec.PublicKey,
	blocksForExpiration int64,
) ([]byte, error) {
	aggregateKey, err := musig.MuSig2CombineKeys(
		musig.Musig2v100,
		[]*btcec.PublicKey{userPubKey, cosignerPubKey},
		musig.NoopTweak(),
	)
	if err != nil {
		return nil, errors.Errorf("aggregate non-collaborative key: %w", err)
	}

	return txscript.NewScriptBuilder().
		AddData(schnorr.SerializePubKey(aggregateKey.PreTweakedKey)).
		AddOp(txscript.OP_CHECKSIGVERIFY).
		AddInt64(blocksForExpiration).
		AddOp(txscript.OP_CHECKSEQUENCEVERIFY).
		Script()
}

// nonCollaborativeLeaf builds the single tapleaf carrying the non-collaborative spending
// policy.
func (o *ToClientV9) nonCollaborativeLeaf() (txscript.TapLeaf, error) {
	script, err := createNonCollaborativeScriptV9(
		o.UserPubKey, o.CosignerPubKey, toClientV9NonCollaborativeDelay(),
	)
	if err != nil {
		return txscript.TapLeaf{}, err
	}
	return txscript.NewBaseTapLeaf(script), nil
}

// internalKey returns the untweaked taproot internal key:
// MuSig2(user + cosigner + lightningServer).
func (o *ToClientV9) internalKey() (*btcec.PublicKey, error) {
	return createInternalKeyV9(o.UserPubKey, o.CosignerPubKey, o.LightningServerPubKey)
}

// tapScriptTree assembles the output's taproot script tree.
func (o *ToClientV9) tapScriptTree() (*txscript.IndexedTapScriptTree, error) {
	nonCollaborativeLeaf, err := o.nonCollaborativeLeaf()
	if err != nil {
		return nil, err
	}
	return txscript.AssembleTaprootScriptTree(nonCollaborativeLeaf), nil
}

// leafControlBlock builds the taproot control block proving the leaf at
// leafIndex belongs to the output's script tree.
func (o *ToClientV9) leafControlBlock(
	tapScriptTree *txscript.IndexedTapScriptTree,
	leafIndex int,
) ([]byte, error) {
	internalKey, err := o.internalKey()
	if err != nil {
		return nil, err
	}
	controlBlock := tapScriptTree.LeafMerkleProofs[leafIndex].ToControlBlock(internalKey)
	controlBlockBytes, err := controlBlock.ToBytes()
	if err != nil {
		return nil, errors.Errorf("serialize control block: %w", err)
	}
	return controlBlockBytes, nil
}

// ComputeUserCollaborativePartialSignature computes the user's partial
// signature for the collaborative key path spend of the input at index.
// userSessionID must be fresh and never reused for any other signing: nonce
// reuse leaks the private key.
func (o *ToClientV9) ComputeUserCollaborativePartialSignature(
	index int,
	tx *wire.MsgTx,
	sigHashes *txscript.TxSigHashes,
	userPrivKey *hd_keys.HDPrivateKey,
	userSessionID []byte,
	cosignerPublicNonce []byte,
	lightningServerPublicNonce []byte,
) ([]byte, error) {
	userECPrivKey, err := deriveSignerECKey(userPrivKey, o.path, o.UserPubKey)
	if err != nil {
		return nil, errors.Errorf("user key: %w", err)
	}
	return o.computeCollaborativePartialSignature(
		index, tx, sigHashes,
		userECPrivKey, userSessionID,
		o.CosignerPubKey, cosignerPublicNonce,
		o.LightningServerPubKey, lightningServerPublicNonce,
	)
}

// ComputeCosignerCollaborativePartialSignature computes the cosigner's
// partial signature for the collaborative key path spend of the input at
// index.
// cosignerSessionID must be fresh and never reused for any other signing:
// nonce reuse leaks the private key.
func (o *ToClientV9) ComputeCosignerCollaborativePartialSignature(
	index int,
	tx *wire.MsgTx,
	sigHashes *txscript.TxSigHashes,
	cosignerPrivKey *hd_keys.HDPrivateKey,
	cosignerSessionID []byte,
	userPublicNonce []byte,
	lightningServerPublicNonce []byte,
) ([]byte, error) {
	cosignerECPrivKey, err := deriveSignerECKey(cosignerPrivKey, o.path, o.CosignerPubKey)
	if err != nil {
		return nil, errors.Errorf("cosigner key: %w", err)
	}
	return o.computeCollaborativePartialSignature(
		index, tx, sigHashes,
		cosignerECPrivKey, cosignerSessionID,
		o.UserPubKey, userPublicNonce,
		o.LightningServerPubKey, lightningServerPublicNonce,
	)
}

// ComputeLightningServerCollaborativePartialSignature computes the lightning
// server's partial signature for the collaborative key path spend of the
// input at index.
// lightningServerSessionID must be fresh and never reused for any other
// signing: nonce reuse leaks the private key.
func (o *ToClientV9) ComputeLightningServerCollaborativePartialSignature(
	index int,
	tx *wire.MsgTx,
	sigHashes *txscript.TxSigHashes,
	lightningServerPrivKey *hd_keys.HDPrivateKey,
	lightningServerSessionID []byte,
	userPublicNonce []byte,
	cosignerPublicNonce []byte,
) ([]byte, error) {
	lightningServerECPrivKey, err := deriveSignerECKey(
		lightningServerPrivKey, o.path, o.LightningServerPubKey,
	)
	if err != nil {
		return nil, errors.Errorf("lightning server key: %w", err)
	}
	return o.computeCollaborativePartialSignature(
		index, tx, sigHashes,
		lightningServerECPrivKey, lightningServerSessionID,
		o.UserPubKey, userPublicNonce,
		o.CosignerPubKey, cosignerPublicNonce,
	)
}

// computeCollaborativePartialSignature computes signerPrivKey's partial
// signature for the collaborative key path spend of the input at index, given
// the other two participants' public keys and nonces.
func (o *ToClientV9) computeCollaborativePartialSignature(
	index int,
	tx *wire.MsgTx,
	sigHashes *txscript.TxSigHashes,
	signerPrivKey *btcec.PrivateKey,
	signerSessionID []byte,
	otherPubKeyA *btcec.PublicKey,
	otherPublicNonceA []byte,
	otherPubKeyB *btcec.PublicKey,
	otherPublicNonceB []byte,
) ([]byte, error) {
	dataToSign, tweak, err := o.collaborativeDataToSign(index, tx, sigHashes)
	if err != nil {
		return nil, err
	}

	return musig.ComputePartialSignature3Of3(
		dataToSign,
		signerPrivKey.Serialize(),
		otherPubKeyA.SerializeCompressed(),
		otherPubKeyB.SerializeCompressed(),
		otherPublicNonceA,
		otherPublicNonceB,
		signerSessionID,
		tweak,
	)
}

// VerifyUserCollaborativePartialSignature checks the user's partial signature
// for the collaborative key path spend of the input at index.
func (o *ToClientV9) VerifyUserCollaborativePartialSignature(
	index int,
	tx *wire.MsgTx,
	sigHashes *txscript.TxSigHashes,
	partialSignature []byte,
	userPublicNonce []byte,
	cosignerPublicNonce []byte,
	lightningServerPublicNonce []byte,
) error {
	return o.verifyCollaborativePartialSignature(
		index, tx, sigHashes, partialSignature,
		o.UserPubKey, userPublicNonce,
		o.CosignerPubKey, cosignerPublicNonce,
		o.LightningServerPubKey, lightningServerPublicNonce,
	)
}

// VerifyCosignerCollaborativePartialSignature checks the cosigner's partial
// signature for the collaborative key path spend of the input at index.
func (o *ToClientV9) VerifyCosignerCollaborativePartialSignature(
	index int,
	tx *wire.MsgTx,
	sigHashes *txscript.TxSigHashes,
	partialSignature []byte,
	cosignerPublicNonce []byte,
	userPublicNonce []byte,
	lightningServerPublicNonce []byte,
) error {
	return o.verifyCollaborativePartialSignature(
		index, tx, sigHashes, partialSignature,
		o.CosignerPubKey, cosignerPublicNonce,
		o.UserPubKey, userPublicNonce,
		o.LightningServerPubKey, lightningServerPublicNonce,
	)
}

// VerifyLightningServerCollaborativePartialSignature checks the lightning
// server's partial signature for the collaborative key path spend of the
// input at index.
func (o *ToClientV9) VerifyLightningServerCollaborativePartialSignature(
	index int,
	tx *wire.MsgTx,
	sigHashes *txscript.TxSigHashes,
	partialSignature []byte,
	lightningServerPublicNonce []byte,
	userPublicNonce []byte,
	cosignerPublicNonce []byte,
) error {
	return o.verifyCollaborativePartialSignature(
		index, tx, sigHashes, partialSignature,
		o.LightningServerPubKey, lightningServerPublicNonce,
		o.UserPubKey, userPublicNonce,
		o.CosignerPubKey, cosignerPublicNonce,
	)
}

// verifyCollaborativePartialSignature checks partialSignature against
// signerPubKey and signerPublicNonce for the collaborative key path spend of
// the input at index, given the other two participants' public keys and
// nonces.
func (o *ToClientV9) verifyCollaborativePartialSignature(
	index int,
	tx *wire.MsgTx,
	sigHashes *txscript.TxSigHashes,
	partialSignature []byte,
	signerPubKey *btcec.PublicKey,
	signerPublicNonce []byte,
	otherPubKeyA *btcec.PublicKey,
	otherPublicNonceA []byte,
	otherPubKeyB *btcec.PublicKey,
	otherPublicNonceB []byte,
) error {
	dataToSign, tweak, err := o.collaborativeDataToSign(index, tx, sigHashes)
	if err != nil {
		return err
	}

	return musig.VerifyPartialSignature3Of3(
		dataToSign,
		signerPubKey.SerializeCompressed(),
		signerPublicNonce,
		otherPubKeyA.SerializeCompressed(),
		otherPubKeyB.SerializeCompressed(),
		otherPublicNonceA,
		otherPublicNonceB,
		partialSignature,
		tweak,
	)
}

// CombineCollaborativeSignatures combines every participant's partial
// signature into the final key path signature. Combining only uses public
// data.
func (o *ToClientV9) CombineCollaborativeSignatures(
	index int,
	tx *wire.MsgTx,
	sigHashes *txscript.TxSigHashes,
	allPublicNonces [][]byte,
	partialSignatures [][]byte,
) ([]byte, error) {
	dataToSign, tweak, err := o.collaborativeDataToSign(index, tx, sigHashes)
	if err != nil {
		return nil, err
	}

	return musig.CombineSignatures3Of3(
		dataToSign,
		o.UserPubKey.SerializeCompressed(),
		o.CosignerPubKey.SerializeCompressed(),
		o.LightningServerPubKey.SerializeCompressed(),
		allPublicNonces,
		partialSignatures,
		tweak,
	)
}

// AssembleCollaborativeWitness assembles the witness for the collaborative
// key path spend from the final aggregated signature.
func (o *ToClientV9) AssembleCollaborativeWitness(
	finalSignature []byte,
) (wire.TxWitness, error) {
	if len(finalSignature) == 0 {
		return nil, errors.New("empty final signature")
	}

	return wire.TxWitness{finalSignature}, nil
}

// ComputeNonCollaborativeFullSignature computes the final signature for the
// non-collaborative leaf spend of the input at index. Both partial signatures of the
// 2-of-2 MuSig2 are computed locally.
// Panics if the input doesn't commit to a BIP68 relative timelock of at least
// the to_client delay.
func (o *ToClientV9) ComputeNonCollaborativeFullSignature(
	index int,
	tx *wire.MsgTx,
	sigHashes *txscript.TxSigHashes,
	userPrivKey, cosignerPrivKey *hd_keys.HDPrivateKey,
) ([]byte, error) {
	checkRelativeTimelockSpend(tx, index, toClientV9NonCollaborativeDelay())

	userECPrivKey, err := deriveSignerECKey(userPrivKey, o.path, o.UserPubKey)
	if err != nil {
		return nil, errors.Errorf("user key: %w", err)
	}
	cosignerECPrivKey, err := deriveSignerECKey(cosignerPrivKey, o.path, o.CosignerPubKey)
	if err != nil {
		return nil, errors.Errorf("cosigner key: %w", err)
	}

	nonCollaborativeLeaf, err := o.nonCollaborativeLeaf()
	if err != nil {
		return nil, err
	}

	// Passing nil prevOutFetcher as it's only used on SIGHASH_ANYONECANPAY path
	dataToSign, err := txscript.CalcTapscriptSignaturehash(
		sigHashes, txscript.SigHashDefault, tx, index, nil, nonCollaborativeLeaf,
	)
	if err != nil {
		return nil, errors.Errorf("calc tapscript signature hash: %w", err)
	}

	return musig.ComputeFullSignature2Of2(
		dataToSign, userECPrivKey, cosignerECPrivKey, musig.NoopTweak(),
	)
}

// AssembleNonCollaborativeWitness assembles the witness for the non-collaborative leaf
// spend from the final signature.
func (o *ToClientV9) AssembleNonCollaborativeWitness(
	finalSignature []byte,
) (wire.TxWitness, error) {
	if len(finalSignature) == 0 {
		return nil, errors.New("empty final signature")
	}

	tapScriptTree, err := o.tapScriptTree()
	if err != nil {
		return nil, err
	}
	controlBlock, err := o.leafControlBlock(tapScriptTree, toClientV9NonCollaborativeLeafIndex)
	if err != nil {
		return nil, err
	}

	// Witness stack bottom -> top: musig(user, cosigner), leafScript, controlBlock.
	return wire.TxWitness{
		finalSignature,
		tapScriptTree.LeafMerkleProofs[toClientV9NonCollaborativeLeafIndex].Script,
		controlBlock,
	}, nil
}

// LongestToClientV9CollaborativeWitness returns a witness of the maximum size
// a ToClientV9 collaborative spend can have: a single push of a 64-byte
// Schnorr signature (SIGHASH_DEFAULT appends no sighash byte). The witness is
// a bare signature push, so any keys yield the exact size.
func LongestToClientV9CollaborativeWitness() wire.TxWitness {
	sample, err := NewToClientV9(longestPublicKeyTriple(), estimationKeyPath)
	preconditions.CheckStatef(err == nil, "build longest sample output: %v", err)
	witness, err := sample.AssembleCollaborativeWitness(make([]byte, schnorr.SignatureSize))
	preconditions.CheckStatef(err == nil, "assemble longest collaborative witness: %v", err)
	return witness
}

// LongestToClientV9NonCollaborativeWitness returns a witness of the maximum
// size a ToClientV9 non-collaborative spend can have, built with a placeholder
// 64-byte Schnorr signature (SIGHASH_DEFAULT appends no sighash byte).
func LongestToClientV9NonCollaborativeWitness() (wire.TxWitness, error) {
	// The spec's 52560 takes the widest script-number encoding a BIP68 delta can have, 3 bytes.
	sample, err := NewToClientV9(longestPublicKeyTriple(), estimationKeyPath)
	if err != nil {
		return nil, err
	}
	return sample.AssembleNonCollaborativeWitness(make([]byte, schnorr.SignatureSize))
}

// collaborativeDataToSign returns the sighash for the key path spend of the
// input at index, and the tweak of its MuSig2 signing session.
func (o *ToClientV9) collaborativeDataToSign(
	index int,
	tx *wire.MsgTx,
	sigHashes *txscript.TxSigHashes,
) ([]byte, *musig.MuSig2Tweaks, error) {
	// Passing nil prevOutFetcher as it's only used on SIGHASH_ANYONECANPAY path
	sigHash, err := txscript.CalcTaprootSignatureHash(
		sigHashes, txscript.SigHashDefault, tx, index, nil,
	)
	if err != nil {
		return nil, nil, errors.Errorf("calc taproot signature hash: %w", err)
	}

	tapScriptTree, err := o.tapScriptTree()
	if err != nil {
		return nil, nil, err
	}
	// The output key commits to the script tree, so the key-path signature is
	// over the internal key tweaked by the script root.
	scriptRootHash := tapScriptTree.RootNode.TapHash()

	return sigHash, musig.TapScriptTweak(scriptRootHash[:]), nil
}
