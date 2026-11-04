package addresses

import (
	"crypto/sha256"

	"github.com/btcsuite/btcd/btcutil"
	"github.com/btcsuite/btcd/chaincfg"
	"github.com/btcsuite/btcd/wire"

	"github.com/muun/libwallet/base/domain/cryptography/hd_keys"
)

// ToClientV8 is the user's balance output of the channel in its native
// segwit (P2WSH) encoding. A single witness script carries both spending
// paths:
//
//	(user AND cosigner AND lightningServer)                 // collaborative
//	OR (user AND cosigner AND older(blocksForExpiration))   // non-collaborative
//
// A lack of lightningServer signature selects the timelock branch.
type ToClientV8 struct {
	preTaprootToClient
	network *chaincfg.Params
}

// toClientV8NonCollaborativeDelay returns the relative timelock the
// non-collaborative leaf waits: to_client delay.
func toClientV8NonCollaborativeDelay() int64 {
	return delays.toClientDelay
}

// NewToClientV8 derives the participant keys to derivationPath and builds the
// output, whose non-collaborative path waits the spec's to_client delay.
// derivationPath must match m/schema:1'/recovery:1'/<change:0|external:1>/<index>.
func NewToClientV8(
	baseKeys *hd_keys.PublicKeyTriple,
	derivationPath string,
) (*ToClientV8, error) {
	keys, err := deriveParticipantECKeys(baseKeys, derivationPath)
	if err != nil {
		return nil, err
	}

	return &ToClientV8{
		preTaprootToClient: newPreTaprootToClient(
			keys.userPubKey,
			keys.cosignerPubKey,
			keys.lightningServerPubKey,
			derivationPath,
			toClientV8NonCollaborativeDelay(),
		),
		network: baseKeys.Network(),
	}, nil
}

// Address returns the P2WSH address paying to this output.
// V7 and V8 share the witness script; only the address encoding differs.
func (o *ToClientV8) Address() (*WalletAddress, error) {
	witnessScript, err := o.witnessScript()
	if err != nil {
		return nil, err
	}
	witnessScriptHash := sha256.Sum256(witnessScript)

	address, err := btcutil.NewAddressWitnessScriptHash(witnessScriptHash[:], o.network)
	if err != nil {
		return nil, err
	}

	return New(V8, o.path, address.EncodeAddress()), nil
}

// LongestToClientV8CollaborativeWitness returns a witness of the maximum size
// a collaborative ToClientV8 spend can have, built with placeholder
// signatures of the longest standard ECDSA encoding.
func LongestToClientV8CollaborativeWitness() (wire.TxWitness, error) {
	// The spec's 52560 takes the widest script-number encoding a BIP68 delta can have, 3 bytes.
	sample, err := NewToClientV8(longestPublicKeyTriple(), estimationKeyPath)
	if err != nil {
		return nil, err
	}
	placeholderSignature := longestStandardECDSASignature()
	return sample.AssembleCollaborativeWitness(
		placeholderSignature, placeholderSignature, placeholderSignature,
	)
}

// LongestToClientV8NonCollaborativeWitness returns a witness of the maximum
// size a non-collaborative ToClientV8 spend can have, built with placeholder
// signatures of the longest standard ECDSA encoding.
func LongestToClientV8NonCollaborativeWitness() (wire.TxWitness, error) {
	// The spec's 52560 takes the widest script-number encoding a BIP68 delta can have, 3 bytes.
	sample, err := NewToClientV8(longestPublicKeyTriple(), estimationKeyPath)
	if err != nil {
		return nil, err
	}
	placeholderSignature := longestStandardECDSASignature()
	return sample.AssembleNonCollaborativeWitness(
		NewNonCollaborativeFullSignatures(placeholderSignature, placeholderSignature),
	)
}
