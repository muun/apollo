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

// ToClientRevocableOutput is the user's revocable balance output for one
// channel state. It is a taproot (P2TR) output with three spending paths:
//
//   - collaborative: taproot key spend path (MuSig2(user + cosigner + lightningServer))
//   - non-collaborative: tapscript leaf for MuSig2(user + cosigner) after
//     the to_client delay
//   - revocation: tapscript leaf for MuSig2(lightningServer + clientRevocation).
//     The revocation key is a fresh key pair for each channel state, whose
//     private key is revealed to the lightning server when the user revokes it.
type ToClientRevocableOutput struct {
	UserPubKey             *btcec.PublicKey
	CosignerPubKey         *btcec.PublicKey
	LightningServerPubKey  *btcec.PublicKey
	ClientRevocationPubKey *btcec.PublicKey
	path                   string
	network                *chaincfg.Params
}

// Leaf indices in the order handed to the script tree:
//
//	              root
//	           /        \
//	non-collaborative  revocation
const (
	toClientRevocableNonCollaborativeLeafIndex = iota
	toClientRevocableRevocationLeafIndex
)

// toClientRevocableNonCollaborativeDelay returns the relative timelock the
// non-collaborative leaf waits: to_client delay.
func toClientRevocableNonCollaborativeDelay() int64 {
	return delays.toClientDelay
}

// NewToClientRevocableOutput derives the participant keys to derivationPath
// and builds the output. derivationPath must match
// m/schema:1'/recovery:1'/<change:0|external:1>/<index>. The non-collaborative
// path waits the spec's to_client delay. Panics if clientRevocationPubKey is nil.
func NewToClientRevocableOutput(
	baseKeys *hd_keys.PublicKeyTriple,
	derivationPath string,
	clientRevocationPubKey *btcec.PublicKey,
) (*ToClientRevocableOutput, error) {
	keys, err := deriveParticipantECKeys(baseKeys, derivationPath)
	if err != nil {
		return nil, err
	}

	return &ToClientRevocableOutput{
		UserPubKey:             keys.userPubKey,
		CosignerPubKey:         keys.cosignerPubKey,
		LightningServerPubKey:  keys.lightningServerPubKey,
		ClientRevocationPubKey: preconditions.CheckNotNil(clientRevocationPubKey),
		path:                   derivationPath,
		network:                baseKeys.Network(),
	}, nil
}

// Address returns the P2TR address paying to this output.
func (o *ToClientRevocableOutput) Address() (*WalletAddress, error) {
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
		ToClientRevocable, o.path, taprootAddress.EncodeAddress(),
	), nil
}

// nonCollaborativeLeaf builds the tapleaf for the user's delayed recovery:
//
//	<musig(user, cosigner)> OP_CHECKSIGVERIFY <blocksForExpiration> OP_CHECKSEQUENCEVERIFY
func (o *ToClientRevocableOutput) nonCollaborativeLeaf() (txscript.TapLeaf, error) {
	aggregateKey, err := musig.MuSig2CombineKeys(
		musig.Musig2v100,
		[]*btcec.PublicKey{o.UserPubKey, o.CosignerPubKey},
		musig.NoopTweak(),
	)
	if err != nil {
		return txscript.TapLeaf{}, err
	}

	script, err := txscript.NewScriptBuilder().
		AddData(schnorr.SerializePubKey(aggregateKey.PreTweakedKey)).
		AddOp(txscript.OP_CHECKSIGVERIFY).
		AddInt64(toClientRevocableNonCollaborativeDelay()).
		AddOp(txscript.OP_CHECKSEQUENCEVERIFY).
		Script()
	if err != nil {
		return txscript.TapLeaf{}, errors.Errorf("build non-collaborative leaf: %w", err)
	}
	return txscript.NewBaseTapLeaf(script), nil
}

// revocationLeaf builds the revocation tapleaf:
//
//	<musig(lightningServer, revocation)> OP_CHECKSIG
func (o *ToClientRevocableOutput) revocationLeaf() (txscript.TapLeaf, error) {
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
func (o *ToClientRevocableOutput) internalKey() (*btcec.PublicKey, error) {
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
func (o *ToClientRevocableOutput) tapScriptTree() (*txscript.IndexedTapScriptTree, error) {
	nonCollaborativeLeaf, err := o.nonCollaborativeLeaf()
	if err != nil {
		return nil, err
	}
	revocationLeaf, err := o.revocationLeaf()
	if err != nil {
		return nil, err
	}
	return txscript.AssembleTaprootScriptTree(nonCollaborativeLeaf, revocationLeaf), nil
}

// leafControlBlock builds the taproot control block proving the leaf at
// leafIndex belongs to the output's script tree.
func (o *ToClientRevocableOutput) leafControlBlock(
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

// ComputeNonCollaborativeFullSignature computes the final signature for the
// non-collaborative leaf spend of the input at index. Both partial signatures of the
// 2-of-2 MuSig2 are computed locally.
// Panics if the input doesn't commit to a BIP68 relative timelock of at least
// the to_client delay.
func (o *ToClientRevocableOutput) ComputeNonCollaborativeFullSignature(
	index int,
	tx *wire.MsgTx,
	sigHashes *txscript.TxSigHashes,
	userPrivKey, cosignerPrivKey *hd_keys.HDPrivateKey,
) ([]byte, error) {
	checkRelativeTimelockSpend(tx, index, toClientRevocableNonCollaborativeDelay())

	userECPrivKey, err := deriveSignerECKey(userPrivKey, o.path, o.UserPubKey)
	if err != nil {
		return nil, errors.Errorf("user key: %w", err)
	}
	cosignerECPrivKey, err := deriveSignerECKey(
		cosignerPrivKey, o.path, o.CosignerPubKey,
	)
	if err != nil {
		return nil, errors.Errorf("cosigner key: %w", err)
	}

	tapScriptTree, err := o.tapScriptTree()
	if err != nil {
		return nil, err
	}

	dataToSign, err := leafInputDataToSign(
		tapScriptTree, toClientRevocableNonCollaborativeLeafIndex, index, tx, sigHashes,
	)
	if err != nil {
		return nil, err
	}

	return musig.ComputeFullSignature2Of2(
		dataToSign, userECPrivKey, cosignerECPrivKey, musig.NoopTweak(),
	)
}

// LongestToClientRevocableNonCollaborativeWitness returns the largest witness
// for the non-collaborative leaf spend, with a 64-byte placeholder for the
// signature (SIGHASH_DEFAULT appends no sighash byte).
func LongestToClientRevocableNonCollaborativeWitness() (wire.TxWitness, error) {
	// The spec's 52560 takes the widest script-number encoding a BIP68 delta can have, 3 bytes.
	sampleOutput, err := longestToClientRevocableOutput()
	if err != nil {
		return nil, err
	}
	return sampleOutput.AssembleNonCollaborativeWitness(make([]byte, schnorr.SignatureSize))
}

// LongestToClientRevocableRevocationWitness returns a maximum-size witness for
// a spend through the revocation leaf, built over placeholder keys and a
// 64-byte placeholder signature (SIGHASH_DEFAULT appends no sighash byte).
func LongestToClientRevocableRevocationWitness() (wire.TxWitness, error) {
	// The timelock only reaches this witness as a 32-byte sibling leaf hash:
	// any valid value produces the same size.
	sampleOutput, err := longestToClientRevocableOutput()
	if err != nil {
		return nil, err
	}

	tapScriptTree, err := sampleOutput.tapScriptTree()
	if err != nil {
		return nil, err
	}
	controlBlock, err := sampleOutput.leafControlBlock(
		tapScriptTree, toClientRevocableRevocationLeafIndex,
	)
	if err != nil {
		return nil, err
	}
	return wire.TxWitness{
		make([]byte, schnorr.SignatureSize),
		tapScriptTree.LeafMerkleProofs[toClientRevocableRevocationLeafIndex].Script,
		controlBlock,
	}, nil
}

// longestToClientRevocableOutput returns a canned output for witness size
// estimation.
func longestToClientRevocableOutput() (*ToClientRevocableOutput, error) {
	sampleKeys := longestPublicKeyTriple()
	sampleRevocationKey, err := sampleKeys.UserPublicKey().ECPubKey()
	if err != nil {
		return nil, err
	}
	return NewToClientRevocableOutput(
		sampleKeys, estimationKeyPath, sampleRevocationKey,
	)
}

// AssembleNonCollaborativeWitness assembles the witness for the non-collaborative leaf
// spend from the final signature.
func (o *ToClientRevocableOutput) AssembleNonCollaborativeWitness(
	finalSignature []byte,
) (wire.TxWitness, error) {
	if len(finalSignature) == 0 {
		return nil, errors.New("empty final signature")
	}

	tapScriptTree, err := o.tapScriptTree()
	if err != nil {
		return nil, err
	}
	controlBlock, err := o.leafControlBlock(
		tapScriptTree, toClientRevocableNonCollaborativeLeafIndex,
	)
	if err != nil {
		return nil, err
	}

	// Witness stack bottom -> top: musig(user, cosigner), leafScript, controlBlock.
	return wire.TxWitness{
		finalSignature,
		tapScriptTree.LeafMerkleProofs[toClientRevocableNonCollaborativeLeafIndex].Script,
		controlBlock,
	}, nil
}

// ComputeUserCollaborativePartialSignature computes the user's partial
// signature for the collaborative key path spend of the input at index.
// userSessionID must be fresh and never reused for any other signing: nonce
// reuse leaks the private key.
func (o *ToClientRevocableOutput) ComputeUserCollaborativePartialSignature(
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
func (o *ToClientRevocableOutput) ComputeCosignerCollaborativePartialSignature(
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
func (o *ToClientRevocableOutput) ComputeLightningServerCollaborativePartialSignature(
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
func (o *ToClientRevocableOutput) computeCollaborativePartialSignature(
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
func (o *ToClientRevocableOutput) VerifyUserCollaborativePartialSignature(
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
func (o *ToClientRevocableOutput) VerifyCosignerCollaborativePartialSignature(
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
func (o *ToClientRevocableOutput) VerifyLightningServerCollaborativePartialSignature(
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
func (o *ToClientRevocableOutput) verifyCollaborativePartialSignature(
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
func (o *ToClientRevocableOutput) CombineCollaborativeSignatures(
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
func (o *ToClientRevocableOutput) AssembleCollaborativeWitness(
	finalSignature []byte,
) (wire.TxWitness, error) {
	if len(finalSignature) == 0 {
		return nil, errors.New("empty final signature")
	}

	return wire.TxWitness{finalSignature}, nil
}

// LongestToClientRevocableCollaborativeWitness returns the largest witness
// for the collaborative key path spend, with a 64-byte placeholder for the
// signature (SIGHASH_DEFAULT appends no sighash byte).
// The size does not depend on the keys or the expiration.
func LongestToClientRevocableCollaborativeWitness() (wire.TxWitness, error) {
	return wire.TxWitness{make([]byte, schnorr.SignatureSize)}, nil
}

// collaborativeDataToSign returns the sighash for the key path spend of the
// input at index, and the tweak of its MuSig2 signing session.
func (o *ToClientRevocableOutput) collaborativeDataToSign(
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
