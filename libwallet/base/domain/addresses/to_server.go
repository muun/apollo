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

// revocationSigHashType commits revocation sweep signatures to the signed
// input alone.
const revocationSigHashType = txscript.SigHashAll | txscript.SigHashAnyOneCanPay

// ToServerOutput is the channel output holding the server's balance for one
// channel state. It is a taproot (P2TR) output with two spending paths:
//
//   - non-collaborative: the lightning server's spend after the to_server delay, the
//     single script in the Tap Tree. The delay gives the user's watchtower time
//     to react to an old state being broadcast.
//   - revocation: taproot key spend path (MuSig2(user + serverRevocation)).
//     The revocation key is a fresh key pair for each channel state, whose private key is
//     revealed to the user when revoking the state. The user's key completes the 2-of-2.
type ToServerOutput struct {
	UserPubKey             *btcec.PublicKey
	LightningServerPubKey  *btcec.PublicKey
	ServerRevocationPubKey *btcec.PublicKey
	path                   string
	network                *chaincfg.Params
}

// The output's script tree has a single leaf.
const toServerNonCollaborativeLeafIndex = 0

// toServerNonCollaborativeDelay returns the relative timelock the
// non-collaborative leaf waits: to_server delay.
func toServerNonCollaborativeDelay() int64 {
	return delays.toServerDelay
}

// NewToServerOutput derives the user and lightning server keys to
// derivationPath and builds the output, whose non-collaborative path waits
// the spec's to_server delay. derivationPath must match
// m/schema:1'/recovery:1'/<change:0|external:1>/<index>. Panics if
// serverRevocationPubKey is nil.
func NewToServerOutput(
	baseKeys *hd_keys.PublicKeyTriple,
	derivationPath string,
	serverRevocationPubKey *btcec.PublicKey,
) (*ToServerOutput, error) {
	keys, err := deriveParticipantECKeys(baseKeys, derivationPath)
	if err != nil {
		return nil, err
	}

	return &ToServerOutput{
		UserPubKey:             keys.userPubKey,
		LightningServerPubKey:  keys.lightningServerPubKey,
		ServerRevocationPubKey: preconditions.CheckNotNil(serverRevocationPubKey),
		path:                   derivationPath,
		network:                baseKeys.Network(),
	}, nil
}

// Address returns the P2TR address paying to this output.
func (o *ToServerOutput) Address() (*WalletAddress, error) {
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
		ToServer, o.path, taprootAddress.EncodeAddress(),
	), nil
}

// nonCollaborativeLeaf builds the single tapleaf carrying the non-collaborative
// policy:
//
//	<lightningServer> OP_CHECKSIGVERIFY <blocksForDelay> OP_CHECKSEQUENCEVERIFY
func (o *ToServerOutput) nonCollaborativeLeaf() (txscript.TapLeaf, error) {
	script, err := txscript.NewScriptBuilder().
		AddData(schnorr.SerializePubKey(o.LightningServerPubKey)).
		AddOp(txscript.OP_CHECKSIGVERIFY).
		AddInt64(toServerNonCollaborativeDelay()).
		AddOp(txscript.OP_CHECKSEQUENCEVERIFY).
		Script()
	if err != nil {
		return txscript.TapLeaf{}, errors.Errorf("build non-collaborative leaf: %w", err)
	}
	return txscript.NewBaseTapLeaf(script), nil
}

// internalKey returns the untweaked taproot internal key:
// MuSig2(user + serverRevocation).
func (o *ToServerOutput) internalKey() (*btcec.PublicKey, error) {
	aggregateKey, err := musig.MuSig2CombineKeys(
		musig.Musig2v100,
		[]*btcec.PublicKey{o.UserPubKey, o.ServerRevocationPubKey},
		musig.NoopTweak(),
	)
	if err != nil {
		return nil, err
	}
	return aggregateKey.PreTweakedKey, nil
}

// tapScriptTree assembles the output's taproot script tree.
func (o *ToServerOutput) tapScriptTree() (*txscript.IndexedTapScriptTree, error) {
	nonCollaborativeLeaf, err := o.nonCollaborativeLeaf()
	if err != nil {
		return nil, err
	}
	return txscript.AssembleTaprootScriptTree(nonCollaborativeLeaf), nil
}

// leafControlBlock builds the taproot control block proving the leaf at
// leafIndex belongs to the output's script tree.
func (o *ToServerOutput) leafControlBlock(
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

// ComputeRevocationFullSignature computes the final signature for the key
// path sweep of the input at index. Both partial signatures of the 2-of-2
// MuSig2 are computed locally: the revocation private key is the one revealed
// when the state was revoked.
// The signature commits to this input alone but to every output
// (SIGHASH_ALL | SIGHASH_ANYONECANPAY), so it survives other inputs being
// added to the spending transaction and fixes its outputs at signing time.
func (o *ToServerOutput) ComputeRevocationFullSignature(
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
	dataToSign, err := keyPathInputDataToSignWithSigHashType(
		index, tx, sigHashes, revocationSigHashType, prevOuts,
	)
	if err != nil {
		return nil, err
	}

	tapScriptTree, err := o.tapScriptTree()
	if err != nil {
		return nil, err
	}
	// The output key commits to the script tree, so the key-path signature is
	// over the internal key tweaked by the script root.
	scriptRootHash := tapScriptTree.RootNode.TapHash()

	return musig.ComputeFullSignature2Of2(
		dataToSign, userECPrivKey, revocationPrivKey, musig.TapScriptTweak(scriptRootHash[:]),
	)
}

// AssembleRevocationWitness assembles the witness for the revocation key path
// spend from the final signature.
func (o *ToServerOutput) AssembleRevocationWitness(
	finalSignature []byte,
) (wire.TxWitness, error) {
	if len(finalSignature) == 0 {
		return nil, errors.New("empty final signature")
	}

	// A taproot signature for any sighash type other than the default carries
	// the type in a 65th byte.
	signature := make([]byte, 0, len(finalSignature)+1)
	signature = append(signature, finalSignature...)
	signature = append(signature, byte(revocationSigHashType))
	return wire.TxWitness{signature}, nil
}

// LongestToServerRevocationWitness returns the longest possible witness for
// the revocation sweep. It sizes the spend before signatures exist: only
// maximum-size signature placeholders stand in for real data.
func LongestToServerRevocationWitness() (wire.TxWitness, error) {
	// The witness is a single signature push, so its size is independent of the
	// keys and of the delay.
	sampleKeys := longestPublicKeyTriple()
	sampleRevocationKey, err := sampleKeys.UserPublicKey().ECPubKey()
	if err != nil {
		return nil, err
	}
	sample, err := NewToServerOutput(
		sampleKeys, estimationKeyPath, sampleRevocationKey,
	)
	if err != nil {
		return nil, err
	}
	return sample.AssembleRevocationWitness(make([]byte, schnorr.SignatureSize))
}

// LongestToServerNonCollaborativeWitness returns a maximum-size witness for a
// spend through the non-collaborative leaf, built over placeholder keys and a
// placeholder signature.
func LongestToServerNonCollaborativeWitness() (wire.TxWitness, error) {
	sampleKeys := longestPublicKeyTriple()
	sampleRevocationKey, err := sampleKeys.UserPublicKey().ECPubKey()
	if err != nil {
		return nil, err
	}
	// The sample commits to the spec's 1008, a 2-byte script number.
	sample, err := NewToServerOutput(
		sampleKeys, estimationKeyPath, sampleRevocationKey,
	)
	if err != nil {
		return nil, err
	}

	tapScriptTree, err := sample.tapScriptTree()
	if err != nil {
		return nil, err
	}
	controlBlock, err := sample.leafControlBlock(tapScriptTree, toServerNonCollaborativeLeafIndex)
	if err != nil {
		return nil, err
	}
	return wire.TxWitness{
		make([]byte, schnorr.SignatureSize),
		tapScriptTree.LeafMerkleProofs[toServerNonCollaborativeLeafIndex].Script,
		controlBlock,
	}, nil
}
