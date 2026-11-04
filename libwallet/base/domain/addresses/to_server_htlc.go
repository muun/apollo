package addresses

import (
	"crypto/sha256"

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

// ToServerHTLCOutput is the channel output holding an in-flight outgoing
// payment for one channel state. It is a taproot (P2TR) output with four
// spending paths:
//
//   - collaborative: taproot key spend path (MuSig2(user + cosigner + lightningServer))
//   - preimage: tapscript leaf requiring the payment preimage plus the
//     3-of-3 signature
//   - non-collaborative: tapscript leaf for MuSig2(user + cosigner) after
//     the to_client delay
//   - revocation: tapscript leaf for MuSig2(user + serverRevocation).
//     The revocation key is a fresh key pair for each channel state, whose
//     private key is revealed to the user when revoking the state.
type ToServerHTLCOutput struct {
	UserPubKey             *btcec.PublicKey
	CosignerPubKey         *btcec.PublicKey
	LightningServerPubKey  *btcec.PublicKey
	ServerRevocationPubKey *btcec.PublicKey
	PaymentHash            []byte
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
	toServerHTLCPreimageLeafIndex = iota
	toServerHTLCRevocationLeafIndex
	toServerHTLCNonCollaborativeLeafIndex
)

// toServerHTLCNonCollaborativeDelay returns the relative timelock the
// non-collaborative leaf waits: to_client delay.
func toServerHTLCNonCollaborativeDelay() int64 {
	return delays.toClientDelay
}

// NewToServerHTLCOutput derives the participant keys to derivationPath and
// builds the output committing to paymentHash, whose non-collaborative path
// waits the spec's to_client delay. derivationPath must match
// m/schema:1'/recovery:1'/<change:0|external:1>/<index>. Panics if
// serverRevocationPubKey is nil or paymentHash isn't 32 bytes.
func NewToServerHTLCOutput(
	baseKeys *hd_keys.PublicKeyTriple,
	derivationPath string,
	serverRevocationPubKey *btcec.PublicKey,
	paymentHash []byte,
) (*ToServerHTLCOutput, error) {
	preconditions.CheckStatef(
		len(paymentHash) == sha256.Size,
		"paymentHash must be %d bytes, got %d", sha256.Size, len(paymentHash),
	)

	keys, err := deriveParticipantECKeys(baseKeys, derivationPath)
	if err != nil {
		return nil, err
	}

	return &ToServerHTLCOutput{
		UserPubKey:             keys.userPubKey,
		CosignerPubKey:         keys.cosignerPubKey,
		LightningServerPubKey:  keys.lightningServerPubKey,
		ServerRevocationPubKey: preconditions.CheckNotNil(serverRevocationPubKey),
		PaymentHash:            paymentHash,
		path:                   derivationPath,
		network:                baseKeys.Network(),
	}, nil
}

// Address returns the P2TR address paying to this output.
func (o *ToServerHTLCOutput) Address() (*WalletAddress, error) {
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
		ToServerHTLC, o.path, taprootAddress.EncodeAddress(),
	), nil
}

// MetadataOutputData returns this output's output_data for the state
// metadata: the tap branch hash of the (preimage, revocation) pair.
func (o *ToServerHTLCOutput) MetadataOutputData() ([]byte, error) {
	tapScriptTree, err := o.tapScriptTree()
	if err != nil {
		return nil, err
	}

	// The non-collaborative leaf sits at depth 1, so its inclusion proof is
	// exactly the (preimage, revocation) branch hash.
	proof := tapScriptTree.LeafMerkleProofs[toServerHTLCNonCollaborativeLeafIndex]
	preconditions.CheckStatef(
		len(proof.InclusionProof) == sha256.Size,
		"expected a depth-1 inclusion proof, got %d bytes", len(proof.InclusionProof),
	)
	return proof.InclusionProof, nil
}

// nonCollaborativeLeaf builds the tapleaf for the user's delayed recovery:
//
//	<musig(user, cosigner)> OP_CHECKSIGVERIFY <blocksForExpiration> OP_CHECKSEQUENCEVERIFY
func (o *ToServerHTLCOutput) nonCollaborativeLeaf() (txscript.TapLeaf, error) {
	return toServerHTLCNonCollaborativeLeaf(
		o.UserPubKey, o.CosignerPubKey,
	)
}

// toServerHTLCNonCollaborativeLeaf builds the tapleaf for the user's delayed
// recovery:
//
//	<musig(user, cosigner)> OP_CHECKSIGVERIFY <blocksForExpiration> OP_CHECKSEQUENCEVERIFY
func toServerHTLCNonCollaborativeLeaf(
	userPubKey, cosignerPubKey *btcec.PublicKey,
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
		AddInt64(toServerHTLCNonCollaborativeDelay()).
		AddOp(txscript.OP_CHECKSEQUENCEVERIFY).
		Script()
	if err != nil {
		return txscript.TapLeaf{}, errors.Errorf("build non-collaborative leaf: %w", err)
	}
	return txscript.NewBaseTapLeaf(script), nil
}

// preimageLeaf builds the tapleaf paying the lightning server once the payment
// preimage is revealed:
//
//	<musig(user, cosigner, lightningServer)> OP_CHECKSIGVERIFY
//	OP_SIZE <32> OP_EQUALVERIFY OP_SHA256 <paymentHash> OP_EQUAL
func (o *ToServerHTLCOutput) preimageLeaf() (txscript.TapLeaf, error) {
	// The leaf key is the same untweaked 3-of-3 aggregate as the internal key.
	key, err := o.internalKey()
	if err != nil {
		return txscript.TapLeaf{}, err
	}

	script, err := txscript.NewScriptBuilder().
		AddData(schnorr.SerializePubKey(key)).
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
//	<musig(user, revocation)> OP_CHECKSIG
func (o *ToServerHTLCOutput) revocationLeaf() (txscript.TapLeaf, error) {
	aggregateKey, err := musig.MuSig2CombineKeys(
		musig.Musig2v100,
		[]*btcec.PublicKey{o.UserPubKey, o.ServerRevocationPubKey},
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
func (o *ToServerHTLCOutput) internalKey() (*btcec.PublicKey, error) {
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
func (o *ToServerHTLCOutput) tapScriptTree() (*txscript.IndexedTapScriptTree, error) {
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
func (o *ToServerHTLCOutput) leafControlBlock(
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
func (o *ToServerHTLCOutput) ComputeUserCollaborativePartialSignature(
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
func (o *ToServerHTLCOutput) ComputeCosignerCollaborativePartialSignature(
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
func (o *ToServerHTLCOutput) ComputeLightningServerCollaborativePartialSignature(
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
func (o *ToServerHTLCOutput) computeCollaborativePartialSignature(
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
func (o *ToServerHTLCOutput) VerifyUserCollaborativePartialSignature(
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
func (o *ToServerHTLCOutput) VerifyCosignerCollaborativePartialSignature(
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
func (o *ToServerHTLCOutput) VerifyLightningServerCollaborativePartialSignature(
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
func (o *ToServerHTLCOutput) verifyCollaborativePartialSignature(
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
func (o *ToServerHTLCOutput) CombineCollaborativeSignatures(
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
func (o *ToServerHTLCOutput) AssembleCollaborativeWitness(
	finalSignature []byte,
) (wire.TxWitness, error) {
	if len(finalSignature) == 0 {
		return nil, errors.New("empty final signature")
	}

	return wire.TxWitness{finalSignature}, nil
}

// LongestToServerHTLCCollaborativeWitness returns the witness of a
// collaborative key path spend with a maximum-size signature placeholder, to
// size the spend before signing. The size is independent of the keys, payment
// hash and timelock.
func LongestToServerHTLCCollaborativeWitness() (wire.TxWitness, error) {
	return wire.TxWitness{make([]byte, schnorr.SignatureSize)}, nil
}

// longestToServerHTLCOutput returns a canned output for witness size
// estimation.
func longestToServerHTLCOutput() (*ToServerHTLCOutput, error) {
	sampleKeys := longestPublicKeyTriple()
	sampleRevocationKey, err := sampleKeys.UserPublicKey().ECPubKey()
	if err != nil {
		return nil, err
	}
	return NewToServerHTLCOutput(
		sampleKeys,
		estimationKeyPath,
		sampleRevocationKey,
		make([]byte, sha256.Size),
	)
}

// ComputeUserPreimagePartialSignature computes the user's partial
// signature for a spend through the preimage leaf of the input at index.
// userSessionID must be fresh and never reused for any other signing: nonce
// reuse leaks the private key.
func (o *ToServerHTLCOutput) ComputeUserPreimagePartialSignature(
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
	return o.computePreimagePartialSignature(
		index, tx, sigHashes,
		userECPrivKey, userSessionID,
		o.CosignerPubKey, cosignerPublicNonce,
		o.LightningServerPubKey, lightningServerPublicNonce,
	)
}

// ComputeCosignerPreimagePartialSignature computes the cosigner's
// partial signature for a spend through the preimage leaf of the input at
// index.
// cosignerSessionID must be fresh and never reused for any other signing:
// nonce reuse leaks the private key.
func (o *ToServerHTLCOutput) ComputeCosignerPreimagePartialSignature(
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
	return o.computePreimagePartialSignature(
		index, tx, sigHashes,
		cosignerECPrivKey, cosignerSessionID,
		o.UserPubKey, userPublicNonce,
		o.LightningServerPubKey, lightningServerPublicNonce,
	)
}

// ComputeLightningServerPreimagePartialSignature computes the lightning
// server's partial signature for a spend through the preimage leaf of the
// input at index.
// lightningServerSessionID must be fresh and never reused for any other
// signing: nonce reuse leaks the private key.
func (o *ToServerHTLCOutput) ComputeLightningServerPreimagePartialSignature(
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
	return o.computePreimagePartialSignature(
		index, tx, sigHashes,
		lightningServerECPrivKey, lightningServerSessionID,
		o.UserPubKey, userPublicNonce,
		o.CosignerPubKey, cosignerPublicNonce,
	)
}

// computePreimagePartialSignature computes signerPrivKey's partial
// signature for a spend through the preimage leaf of the input at index,
// given the other two participants' public keys and nonces.
func (o *ToServerHTLCOutput) computePreimagePartialSignature(
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
	dataToSign, err := o.preimageDataToSign(index, tx, sigHashes)
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
		musig.NoopTweak(),
	)
}

// VerifyUserPreimagePartialSignature checks the user's partial signature
// for a spend through the preimage leaf of the input at index.
func (o *ToServerHTLCOutput) VerifyUserPreimagePartialSignature(
	index int,
	tx *wire.MsgTx,
	sigHashes *txscript.TxSigHashes,
	partialSignature []byte,
	userPublicNonce []byte,
	cosignerPublicNonce []byte,
	lightningServerPublicNonce []byte,
) error {
	return o.verifyPreimagePartialSignature(
		index, tx, sigHashes, partialSignature,
		o.UserPubKey, userPublicNonce,
		o.CosignerPubKey, cosignerPublicNonce,
		o.LightningServerPubKey, lightningServerPublicNonce,
	)
}

// VerifyCosignerPreimagePartialSignature checks the cosigner's partial
// signature for a spend through the preimage leaf of the input at index.
func (o *ToServerHTLCOutput) VerifyCosignerPreimagePartialSignature(
	index int,
	tx *wire.MsgTx,
	sigHashes *txscript.TxSigHashes,
	partialSignature []byte,
	cosignerPublicNonce []byte,
	userPublicNonce []byte,
	lightningServerPublicNonce []byte,
) error {
	return o.verifyPreimagePartialSignature(
		index, tx, sigHashes, partialSignature,
		o.CosignerPubKey, cosignerPublicNonce,
		o.UserPubKey, userPublicNonce,
		o.LightningServerPubKey, lightningServerPublicNonce,
	)
}

// VerifyLightningServerPreimagePartialSignature checks the lightning
// server's partial signature for a spend through the preimage leaf of the
// input at index.
func (o *ToServerHTLCOutput) VerifyLightningServerPreimagePartialSignature(
	index int,
	tx *wire.MsgTx,
	sigHashes *txscript.TxSigHashes,
	partialSignature []byte,
	lightningServerPublicNonce []byte,
	userPublicNonce []byte,
	cosignerPublicNonce []byte,
) error {
	return o.verifyPreimagePartialSignature(
		index, tx, sigHashes, partialSignature,
		o.LightningServerPubKey, lightningServerPublicNonce,
		o.UserPubKey, userPublicNonce,
		o.CosignerPubKey, cosignerPublicNonce,
	)
}

// verifyPreimagePartialSignature checks partialSignature against
// signerPubKey and signerPublicNonce for a spend through the preimage leaf of
// the input at index, given the other two participants' public keys and
// nonces.
func (o *ToServerHTLCOutput) verifyPreimagePartialSignature(
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
	dataToSign, err := o.preimageDataToSign(index, tx, sigHashes)
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
		musig.NoopTweak(),
	)
}

// CombinePreimageSignatures combines every participant's partial
// signature into the final preimage leaf signature. Combining only uses
// public data.
func (o *ToServerHTLCOutput) CombinePreimageSignatures(
	index int,
	tx *wire.MsgTx,
	sigHashes *txscript.TxSigHashes,
	allPublicNonces [][]byte,
	partialSignatures [][]byte,
) ([]byte, error) {
	dataToSign, err := o.preimageDataToSign(index, tx, sigHashes)
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
		musig.NoopTweak(),
	)
}

// AssemblePreimageWitness assembles the witness for a spend through the
// preimage leaf from the final aggregated signature and the payment preimage.
func (o *ToServerHTLCOutput) AssemblePreimageWitness(
	finalSignature []byte,
	preimage []byte,
) (wire.TxWitness, error) {
	if len(finalSignature) == 0 {
		return nil, errors.New("empty final signature")
	}

	tapScriptTree, err := o.tapScriptTree()
	if err != nil {
		return nil, err
	}
	controlBlock, err := o.leafControlBlock(tapScriptTree, toServerHTLCPreimageLeafIndex)
	if err != nil {
		return nil, err
	}

	return wire.TxWitness{
		preimage,
		finalSignature,
		tapScriptTree.LeafMerkleProofs[toServerHTLCPreimageLeafIndex].Script,
		controlBlock,
	}, nil
}

// ComputeToServerHTLCNonCollaborativeFullSignature computes the final
// signature for the non-collaborative leaf spend of the input at index. Both
// partial signatures of the 2-of-2 MuSig2 are computed locally.
// Panics if the input doesn't commit to a BIP68 relative timelock of at least
// the spec's to_client delay.
func ComputeToServerHTLCNonCollaborativeFullSignature(
	index int,
	tx *wire.MsgTx,
	sigHashes *txscript.TxSigHashes,
	userPrivKey, cosignerPrivKey *hd_keys.HDPrivateKey,
	baseKeys *hd_keys.PublicKeyTriple,
	derivationPath string,
) ([]byte, error) {
	checkRelativeTimelockSpend(tx, index, toServerHTLCNonCollaborativeDelay())

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

	nonCollaborativeLeaf, err := toServerHTLCNonCollaborativeLeaf(
		keys.userPubKey, keys.cosignerPubKey,
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

// AssembleToServerHTLCNonCollaborativeWitness assembles the witness for the
// non-collaborative leaf spend from the final signature, the participant keys
// derived to derivationPath, and the (preimage, revocation) branch hash
// committed in the state metadata.
func AssembleToServerHTLCNonCollaborativeWitness(
	finalSignature []byte,
	baseKeys *hd_keys.PublicKeyTriple,
	derivationPath string,
	branchHash []byte,
) (wire.TxWitness, error) {
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
	nonCollaborativeLeaf, err := toServerHTLCNonCollaborativeLeaf(
		keys.userPubKey, keys.cosignerPubKey,
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

// LongestToServerHTLCNonCollaborativeWitness returns the witness of a
// non-collaborative leaf spend with a maximum-size signature placeholder, to
// size the spend before signing.
func LongestToServerHTLCNonCollaborativeWitness() (wire.TxWitness, error) {
	// The spec's 52560 takes the widest script-number encoding a BIP68 delta
	// can have, 3 bytes. The branch hash only sizes a fixed-width control
	// block node.
	return AssembleToServerHTLCNonCollaborativeWitness(
		make([]byte, schnorr.SignatureSize),
		longestPublicKeyTriple(),
		estimationKeyPath,
		make([]byte, sha256.Size),
	)
}

// ComputeRevocationFullSignature computes the final signature for the
// revocation leaf sweep of the input at index. Both partial signatures of the
// 2-of-2 MuSig2 are computed locally: the revocation private key is the one
// revealed when the state was revoked.
// The signature commits to this input alone but to every output
// (SIGHASH_ALL | SIGHASH_ANYONECANPAY), so it survives other inputs being
// added to the spending transaction and fixes its outputs at signing time.
func (o *ToServerHTLCOutput) ComputeRevocationFullSignature(
	index int,
	tx *wire.MsgTx,
	sigHashes *txscript.TxSigHashes,
	prevOuts txscript.PrevOutputFetcher,
	userPrivKey *hd_keys.HDPrivateKey,
	revocationPrivKey *btcec.PrivateKey,
) ([]byte, error) {
	userECPrivKey, err := deriveSignerECKey(userPrivKey, o.path, o.UserPubKey)
	if err != nil {
		return nil, errors.Errorf("user key: %w", err)
	}
	if !revocationPrivKey.PubKey().IsEqual(o.ServerRevocationPubKey) {
		return nil, errors.New("the private key does not match the revocation public key")
	}

	tapScriptTree, err := o.tapScriptTree()
	if err != nil {
		return nil, err
	}

	dataToSign, err := leafInputDataToSignWithSigHashType(
		tapScriptTree, toServerHTLCRevocationLeafIndex, index, tx, sigHashes,
		revocationSigHashType, prevOuts,
	)
	if err != nil {
		return nil, err
	}

	return musig.ComputeFullSignature2Of2(
		dataToSign, userECPrivKey, revocationPrivKey, musig.NoopTweak(),
	)
}

// AssembleRevocationWitness assembles the witness for the revocation leaf
// sweep from the final signature.
func (o *ToServerHTLCOutput) AssembleRevocationWitness(
	finalSignature []byte,
) (wire.TxWitness, error) {
	if len(finalSignature) == 0 {
		return nil, errors.New("empty final signature")
	}

	tapScriptTree, err := o.tapScriptTree()
	if err != nil {
		return nil, err
	}
	controlBlock, err := o.leafControlBlock(tapScriptTree, toServerHTLCRevocationLeafIndex)
	if err != nil {
		return nil, err
	}

	// A taproot signature for any sighash type other than the default carries
	// the type in a 65th byte.
	signature := make([]byte, 0, len(finalSignature)+1)
	signature = append(signature, finalSignature...)
	signature = append(signature, byte(revocationSigHashType))
	return wire.TxWitness{
		signature,
		tapScriptTree.LeafMerkleProofs[toServerHTLCRevocationLeafIndex].Script,
		controlBlock,
	}, nil
}

// LongestToServerHTLCRevocationWitness returns the witness of a revocation
// leaf sweep with a maximum-size signature placeholder, to size the sweep
// before signing. The size is independent of the keys, payment hash and
// timelock: the CSV timelock lives in a sibling leaf, which only contributes
// a fixed-size merkle hash.
func LongestToServerHTLCRevocationWitness() (wire.TxWitness, error) {
	sample, err := longestToServerHTLCOutput()
	if err != nil {
		return nil, err
	}
	// The sighash type byte is appended by AssembleRevocationWitness itself.
	return sample.AssembleRevocationWitness(make([]byte, schnorr.SignatureSize))
}

// LongestToServerHTLCPreimageWitness returns a maximum-size witness for
// a spend through the preimage leaf, built over placeholder keys, a
// placeholder signature and a placeholder preimage.
func LongestToServerHTLCPreimageWitness() (wire.TxWitness, error) {
	// The timelock only reaches this witness as a 32-byte sibling leaf hash:
	// any valid value produces the same size.
	sample, err := longestToServerHTLCOutput()
	if err != nil {
		return nil, err
	}
	return sample.AssemblePreimageWitness(
		make([]byte, schnorr.SignatureSize), make([]byte, sha256.Size),
	)
}

// collaborativeDataToSign returns the sighash for the key path spend of the
// input at index, and the tweak of its MuSig2 signing session.
func (o *ToServerHTLCOutput) collaborativeDataToSign(
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

// preimageDataToSign returns the sighash for a spend through the
// preimage leaf of the input at index.
func (o *ToServerHTLCOutput) preimageDataToSign(
	index int,
	tx *wire.MsgTx,
	sigHashes *txscript.TxSigHashes,
) ([]byte, error) {
	tapScriptTree, err := o.tapScriptTree()
	if err != nil {
		return nil, err
	}
	return leafInputDataToSign(
		tapScriptTree, toServerHTLCPreimageLeafIndex, index, tx, sigHashes,
	)
}
