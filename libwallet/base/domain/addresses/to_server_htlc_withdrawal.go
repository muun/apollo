package addresses

import (
	"crypto/sha256"
	"encoding/binary"

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

// ToServerHTLCWithdrawalOutput is the withdrawal output holding an in-flight
// outgoing payment. It is a taproot (P2TR) output with four spending paths:
//
//   - collaborative: taproot key spend path (MuSig2(user + cosigner + lightningServer))
//   - preimage: tapscript leaf paying the lightning server alone once the
//     payment preimage is revealed
//   - non-collaborative: tapscript leaf for MuSig2(user + cosigner) after the
//     HTLCTimeout block height and the to_client_withdrawal delay from confirmation
//   - revocation: tapscript leaf for MuSig2(lightningServer + clientRevocation).
//     The revocation key is a fresh key pair for each withdrawal, whose private
//     key is revealed to the lightning server when the user revokes it.
type ToServerHTLCWithdrawalOutput struct {
	UserPubKey             *btcec.PublicKey
	CosignerPubKey         *btcec.PublicKey
	LightningServerPubKey  *btcec.PublicKey
	ClientRevocationPubKey *btcec.PublicKey
	PaymentHash            []byte
	HTLCTimeout            int64
	path                   string
	network                *chaincfg.Params
}

// Leaf indices in the order handed to the script tree:
//
//	          root
//	         /    \
//	    branch     non-collaborative
//	    /    \
//	preimage  revocation
const (
	toServerHTLCWithdrawalPreimageLeafIndex = iota
	toServerHTLCWithdrawalRevocationLeafIndex
	toServerHTLCWithdrawalNonCollaborativeLeafIndex
)

// toServerHTLCWithdrawalNonCollaborativeDelay returns the relative timelock the
// non-collaborative leaf waits: to_client_withdrawal delay.
func toServerHTLCWithdrawalNonCollaborativeDelay() int64 {
	return delays.toClientWithdrawalDelay
}

// NewToServerHTLCWithdrawalOutput derives the participant keys to
// derivationPath and builds the output committing to paymentHash and
// htlcTimeout, whose non-collaborative path also waits the spec's
// to_client_withdrawal delay. derivationPath must match
// m/schema:1'/recovery:1'/<change:0|external:1>/<index>. Panics if
// clientRevocationPubKey is nil, paymentHash isn't 32 bytes or htlcTimeout
// isn't a BIP65 block height.
func NewToServerHTLCWithdrawalOutput(
	baseKeys *hd_keys.PublicKeyTriple,
	derivationPath string,
	clientRevocationPubKey *btcec.PublicKey,
	paymentHash []byte,
	htlcTimeout int64,
) (*ToServerHTLCWithdrawalOutput, error) {
	checkAbsoluteTimelock(htlcTimeout)
	preconditions.CheckStatef(
		len(paymentHash) == sha256.Size,
		"paymentHash must be %d bytes, got %d", sha256.Size, len(paymentHash),
	)

	keys, err := deriveParticipantECKeys(baseKeys, derivationPath)
	if err != nil {
		return nil, err
	}

	return &ToServerHTLCWithdrawalOutput{
		UserPubKey:             keys.userPubKey,
		CosignerPubKey:         keys.cosignerPubKey,
		LightningServerPubKey:  keys.lightningServerPubKey,
		ClientRevocationPubKey: preconditions.CheckNotNil(clientRevocationPubKey),
		PaymentHash:            paymentHash,
		HTLCTimeout:            htlcTimeout,
		path:                   derivationPath,
		network:                baseKeys.Network(),
	}, nil
}

// Address returns the P2TR address paying to this output.
func (o *ToServerHTLCWithdrawalOutput) Address() (*WalletAddress, error) {
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

	return New(
		ToServerHTLCWithdrawal, o.path, taprootAddress.EncodeAddress(),
	), nil
}

// MetadataOutputData returns this output's output_data for the state
// metadata: the (preimage, revocation) branch hash followed by HTLCTimeout as
// 4 little-endian bytes.
func (o *ToServerHTLCWithdrawalOutput) MetadataOutputData() ([]byte, error) {
	tapScriptTree, err := o.tapScriptTree()
	if err != nil {
		return nil, err
	}

	// The non-collaborative leaf sits at depth 1, so its inclusion proof is
	// exactly the (preimage, revocation) branch hash.
	proof := tapScriptTree.LeafMerkleProofs[toServerHTLCWithdrawalNonCollaborativeLeafIndex]
	preconditions.CheckStatef(
		len(proof.InclusionProof) == sha256.Size,
		"expected a depth-1 inclusion proof, got %d bytes", len(proof.InclusionProof),
	)
	data := make([]byte, 0, sha256.Size+4)
	data = append(data, proof.InclusionProof...)
	data = binary.LittleEndian.AppendUint32(data, uint32(o.HTLCTimeout))
	return data, nil
}

// nonCollaborativeLeaf builds the tapleaf for the user's delayed recovery:
//
//	<musig(user, cosigner)> OP_CHECKSIGVERIFY
//	<htlcTimeout> OP_CHECKLOCKTIMEVERIFY OP_VERIFY
//	<blocksForDelay> OP_CHECKSEQUENCEVERIFY
func (o *ToServerHTLCWithdrawalOutput) nonCollaborativeLeaf() (txscript.TapLeaf, error) {
	return toServerHTLCWithdrawalNonCollaborativeLeaf(
		o.UserPubKey, o.CosignerPubKey, o.HTLCTimeout,
	)
}

// toServerHTLCWithdrawalNonCollaborativeLeaf builds the tapleaf for the user's
// delayed recovery:
//
//	<musig(user, cosigner)> OP_CHECKSIGVERIFY
//	<htlcTimeout> OP_CHECKLOCKTIMEVERIFY OP_VERIFY
//	<blocksForDelay> OP_CHECKSEQUENCEVERIFY
func toServerHTLCWithdrawalNonCollaborativeLeaf(
	userPubKey, cosignerPubKey *btcec.PublicKey,
	htlcTimeout int64,
) (txscript.TapLeaf, error) {
	aggregateKey, err := musig.MuSig2CombineKeys(
		musig.Musig2v100,
		[]*btcec.PublicKey{userPubKey, cosignerPubKey},
		musig.NoopTweak(),
	)
	if err != nil {
		return txscript.TapLeaf{}, err
	}

	script, err := txscript.NewScriptBuilder().
		AddData(schnorr.SerializePubKey(aggregateKey.PreTweakedKey)).
		AddOp(txscript.OP_CHECKSIGVERIFY).
		AddInt64(htlcTimeout).
		AddOp(txscript.OP_CHECKLOCKTIMEVERIFY).
		AddOp(txscript.OP_VERIFY).
		AddInt64(toServerHTLCWithdrawalNonCollaborativeDelay()).
		AddOp(txscript.OP_CHECKSEQUENCEVERIFY).
		Script()
	if err != nil {
		return txscript.TapLeaf{}, errors.Errorf("build non-collaborative leaf: %w", err)
	}
	return txscript.NewBaseTapLeaf(script), nil
}

// preimageLeaf builds the tapleaf paying the lightning server alone once the
// payment preimage is revealed:
//
//	<lightningServer> OP_CHECKSIGVERIFY
//	OP_SIZE <32> OP_EQUALVERIFY OP_SHA256 <paymentHash> OP_EQUAL
func (o *ToServerHTLCWithdrawalOutput) preimageLeaf() (txscript.TapLeaf, error) {
	script, err := txscript.NewScriptBuilder().
		AddData(schnorr.SerializePubKey(o.LightningServerPubKey)).
		AddOp(txscript.OP_CHECKSIGVERIFY).
		AddOp(txscript.OP_SIZE).
		AddInt64(sha256.Size).
		AddOp(txscript.OP_EQUALVERIFY).
		AddOp(txscript.OP_SHA256).
		AddData(o.PaymentHash).
		AddOp(txscript.OP_EQUAL).
		Script()
	if err != nil {
		return txscript.TapLeaf{}, errors.Errorf("build preimage leaf: %w", err)
	}
	return txscript.NewBaseTapLeaf(script), nil
}

// revocationLeaf builds the revocation tapleaf:
//
//	<musig(lightningServer, revocation)> OP_CHECKSIG
func (o *ToServerHTLCWithdrawalOutput) revocationLeaf() (txscript.TapLeaf, error) {
	aggregateKey, err := musig.MuSig2CombineKeys(
		musig.Musig2v100,
		[]*btcec.PublicKey{o.LightningServerPubKey, o.ClientRevocationPubKey},
		musig.NoopTweak(),
	)
	if err != nil {
		return txscript.TapLeaf{}, err
	}

	script, err := txscript.NewScriptBuilder().
		AddData(schnorr.SerializePubKey(aggregateKey.PreTweakedKey)).
		AddOp(txscript.OP_CHECKSIG).
		Script()
	if err != nil {
		return txscript.TapLeaf{}, errors.Errorf("build revocation leaf: %w", err)
	}
	return txscript.NewBaseTapLeaf(script), nil
}

// internalKey returns the untweaked taproot internal key:
// MuSig2(user + cosigner + lightningServer).
func (o *ToServerHTLCWithdrawalOutput) internalKey() (*btcec.PublicKey, error) {
	aggregateKey, err := musig.MuSig2CombineKeys(
		musig.Musig2v100,
		[]*btcec.PublicKey{o.UserPubKey, o.CosignerPubKey, o.LightningServerPubKey},
		musig.NoopTweak(),
	)
	if err != nil {
		return nil, err
	}
	return aggregateKey.PreTweakedKey, nil
}

// tapScriptTree assembles the taproot script tree in the pinned leaf order.
func (o *ToServerHTLCWithdrawalOutput) tapScriptTree() (*txscript.IndexedTapScriptTree, error) {
	preimageLeaf, err := o.preimageLeaf()
	if err != nil {
		return nil, err
	}
	revocationLeaf, err := o.revocationLeaf()
	if err != nil {
		return nil, err
	}
	nonCollaborativeLeaf, err := o.nonCollaborativeLeaf()
	if err != nil {
		return nil, err
	}
	return txscript.AssembleTaprootScriptTree(
		preimageLeaf, revocationLeaf, nonCollaborativeLeaf,
	), nil
}

// leafControlBlock builds the taproot control block proving the leaf at
// leafIndex belongs to the output's script tree.
func (o *ToServerHTLCWithdrawalOutput) leafControlBlock(
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
func (o *ToServerHTLCWithdrawalOutput) ComputeUserCollaborativePartialSignature(
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
func (o *ToServerHTLCWithdrawalOutput) ComputeCosignerCollaborativePartialSignature(
	index int,
	tx *wire.MsgTx,
	sigHashes *txscript.TxSigHashes,
	cosignerPrivKey *hd_keys.HDPrivateKey,
	cosignerSessionID []byte,
	userPublicNonce []byte,
	lightningServerPublicNonce []byte,
) ([]byte, error) {
	cosignerECPrivKey, err := deriveSignerECKey(
		cosignerPrivKey, o.path, o.CosignerPubKey,
	)
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
func (o *ToServerHTLCWithdrawalOutput) ComputeLightningServerCollaborativePartialSignature(
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
func (o *ToServerHTLCWithdrawalOutput) computeCollaborativePartialSignature(
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
func (o *ToServerHTLCWithdrawalOutput) VerifyUserCollaborativePartialSignature(
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
func (o *ToServerHTLCWithdrawalOutput) VerifyCosignerCollaborativePartialSignature(
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
func (o *ToServerHTLCWithdrawalOutput) VerifyLightningServerCollaborativePartialSignature(
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
func (o *ToServerHTLCWithdrawalOutput) verifyCollaborativePartialSignature(
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
func (o *ToServerHTLCWithdrawalOutput) CombineCollaborativeSignatures(
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
func (o *ToServerHTLCWithdrawalOutput) AssembleCollaborativeWitness(
	finalSignature []byte,
) (wire.TxWitness, error) {
	if len(finalSignature) == 0 {
		return nil, errors.New("empty final signature")
	}

	return wire.TxWitness{finalSignature}, nil
}

// LongestToServerHTLCWithdrawalCollaborativeWitness returns the witness of a
// collaborative key path spend with a maximum-size signature placeholder, to
// size the spend before signing. The size is independent of the keys, payment
// hash and timelocks.
func LongestToServerHTLCWithdrawalCollaborativeWitness() (wire.TxWitness, error) {
	return wire.TxWitness{make([]byte, schnorr.SignatureSize)}, nil
}

// longestToServerHTLCWithdrawalOutput returns a canned output for witness
// size estimation.
func longestToServerHTLCWithdrawalOutput(
	htlcTimeout int64,
) (*ToServerHTLCWithdrawalOutput, error) {
	sampleKeys := longestPublicKeyTriple()
	sampleRevocationKey, err := sampleKeys.UserPublicKey().ECPubKey()
	if err != nil {
		return nil, err
	}
	return NewToServerHTLCWithdrawalOutput(
		sampleKeys,
		estimationKeyPath,
		sampleRevocationKey,
		make([]byte, sha256.Size),
		htlcTimeout,
	)
}

// ComputeToServerHTLCWithdrawalNonCollaborativeFullSignature computes the
// final signature for the non-collaborative leaf spend of the input at index.
// Both partial signatures of the 2-of-2 MuSig2 are computed locally.
// Panics if the transaction doesn't commit to a BIP65 block height of at
// least htlcTimeout and a BIP68 relative timelock of at least the spec's
// to_client_withdrawal delay on the input.
func ComputeToServerHTLCWithdrawalNonCollaborativeFullSignature(
	index int,
	tx *wire.MsgTx,
	sigHashes *txscript.TxSigHashes,
	userPrivKey, cosignerPrivKey *hd_keys.HDPrivateKey,
	baseKeys *hd_keys.PublicKeyTriple,
	derivationPath string,
	htlcTimeout int64,
) ([]byte, error) {
	checkAbsoluteTimelockSpend(tx, index, htlcTimeout)
	checkRelativeTimelockSpend(tx, index, toServerHTLCWithdrawalNonCollaborativeDelay())

	keys, err := deriveParticipantECKeys(baseKeys, derivationPath)
	if err != nil {
		return nil, err
	}
	userECPrivKey, err := deriveSignerECKey(
		userPrivKey, derivationPath, keys.userPubKey,
	)
	if err != nil {
		return nil, errors.Errorf("user key: %w", err)
	}
	cosignerECPrivKey, err := deriveSignerECKey(
		cosignerPrivKey, derivationPath, keys.cosignerPubKey,
	)
	if err != nil {
		return nil, errors.Errorf("cosigner key: %w", err)
	}

	nonCollaborativeLeaf, err := toServerHTLCWithdrawalNonCollaborativeLeaf(
		keys.userPubKey, keys.cosignerPubKey, htlcTimeout,
	)
	if err != nil {
		return nil, err
	}

	// leafInputDataToSign only reads the leaf from the tree, so a single-leaf
	// tree is enough.
	dataToSign, err := leafInputDataToSign(
		txscript.AssembleTaprootScriptTree(nonCollaborativeLeaf), 0, index, tx, sigHashes,
	)
	if err != nil {
		return nil, err
	}

	return musig.ComputeFullSignature2Of2(
		dataToSign, userECPrivKey, cosignerECPrivKey, musig.NoopTweak(),
	)
}

// AssembleToServerHTLCWithdrawalNonCollaborativeWitness assembles the witness
// for the non-collaborative leaf spend from the final signature, the
// participant keys derived to derivationPath, and the state metadata: the
// (preimage, revocation) branch hash and the HTLC timeout.
func AssembleToServerHTLCWithdrawalNonCollaborativeWitness(
	finalSignature []byte,
	baseKeys *hd_keys.PublicKeyTriple,
	derivationPath string,
	htlcTimeout int64,
	branchHash []byte,
) (wire.TxWitness, error) {
	checkAbsoluteTimelock(htlcTimeout)

	if len(finalSignature) == 0 {
		return nil, errors.New("empty final signature")
	}
	if len(branchHash) != sha256.Size {
		return nil, errors.Errorf(
			"expected a %d-byte branch hash, got %d", sha256.Size, len(branchHash),
		)
	}

	keys, err := deriveParticipantECKeys(baseKeys, derivationPath)
	if err != nil {
		return nil, err
	}
	nonCollaborativeLeaf, err := toServerHTLCWithdrawalNonCollaborativeLeaf(
		keys.userPubKey, keys.cosignerPubKey, htlcTimeout,
	)
	if err != nil {
		return nil, err
	}

	internalKey, err := musig.MuSig2CombineKeys(
		musig.Musig2v100,
		[]*btcec.PublicKey{keys.userPubKey, keys.cosignerPubKey, keys.lightningServerPubKey},
		musig.NoopTweak(),
	)
	if err != nil {
		return nil, err
	}
	controlBlock, err := siblingControlBlock(
		internalKey.PreTweakedKey, nonCollaborativeLeaf, branchHash,
	)
	if err != nil {
		return nil, err
	}

	// Witness stack bottom -> top: musig(user, cosigner), leafScript, controlBlock.
	return wire.TxWitness{
		finalSignature,
		nonCollaborativeLeaf.Script,
		controlBlock,
	}, nil
}

// LongestToServerHTLCWithdrawalNonCollaborativeWitness returns the witness of
// a non-collaborative leaf spend with a maximum-size signature placeholder
// and the widest encoding of the HTLC timeout, to size the spend before
// signing.
func LongestToServerHTLCWithdrawalNonCollaborativeWitness() (wire.TxWitness, error) {
	// The largest BIP65 block height takes the widest script-number encoding;
	// the spec's 288-block delay is a 2-byte one. The branch hash only sizes a
	// fixed-width control block node.
	return AssembleToServerHTLCWithdrawalNonCollaborativeWitness(
		make([]byte, schnorr.SignatureSize),
		longestPublicKeyTriple(),
		estimationKeyPath,
		txscript.LockTimeThreshold-1,
		make([]byte, sha256.Size),
	)
}

// LongestToServerHTLCWithdrawalPreimageWitness returns a maximum-size
// witness for a spend through the preimage leaf, built over placeholder keys,
// a placeholder signature and a placeholder preimage.
func LongestToServerHTLCWithdrawalPreimageWitness() (wire.TxWitness, error) {
	// The timelocks only reach this witness as a 32-byte sibling hash: any
	// valid values produce the same size.
	sample, err := longestToServerHTLCWithdrawalOutput(1)
	if err != nil {
		return nil, err
	}

	tapScriptTree, err := sample.tapScriptTree()
	if err != nil {
		return nil, err
	}
	controlBlock, err := sample.leafControlBlock(
		tapScriptTree, toServerHTLCWithdrawalPreimageLeafIndex,
	)
	if err != nil {
		return nil, err
	}
	return wire.TxWitness{
		make([]byte, sha256.Size),
		make([]byte, schnorr.SignatureSize),
		tapScriptTree.LeafMerkleProofs[toServerHTLCWithdrawalPreimageLeafIndex].Script,
		controlBlock,
	}, nil
}

// LongestToServerHTLCWithdrawalRevocationWitness returns a maximum-size witness
// for a spend through the revocation leaf, built over placeholder keys and a
// 64-byte placeholder signature (SIGHASH_DEFAULT appends no sighash byte).
func LongestToServerHTLCWithdrawalRevocationWitness() (wire.TxWitness, error) {
	// The timelocks only reach this witness as a 32-byte sibling hash: any
	// valid values produce the same size.
	sample, err := longestToServerHTLCWithdrawalOutput(1)
	if err != nil {
		return nil, err
	}

	tapScriptTree, err := sample.tapScriptTree()
	if err != nil {
		return nil, err
	}
	controlBlock, err := sample.leafControlBlock(
		tapScriptTree, toServerHTLCWithdrawalRevocationLeafIndex,
	)
	if err != nil {
		return nil, err
	}
	return wire.TxWitness{
		make([]byte, schnorr.SignatureSize),
		tapScriptTree.LeafMerkleProofs[toServerHTLCWithdrawalRevocationLeafIndex].Script,
		controlBlock,
	}, nil
}

// collaborativeDataToSign returns the sighash for the key path spend of the
// input at index, and the tweak of its MuSig2 signing session.
func (o *ToServerHTLCWithdrawalOutput) collaborativeDataToSign(
	index int,
	tx *wire.MsgTx,
	sigHashes *txscript.TxSigHashes,
) ([]byte, *musig.MuSig2Tweaks, error) {
	sigHash, err := keyPathInputDataToSign(index, tx, sigHashes)
	if err != nil {
		return nil, nil, err
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
