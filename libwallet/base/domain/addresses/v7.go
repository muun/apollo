package addresses

import (
	"crypto/sha256"

	"github.com/btcsuite/btcd/btcutil"
	"github.com/btcsuite/btcd/chaincfg"
	"github.com/btcsuite/btcd/txscript"
	"github.com/btcsuite/btcd/wire"

	"github.com/muun/libwallet/base/domain/cryptography/hd_keys"
)

// ToClientV7 is the user's balance output of the channel in its wrapped
// segwit (P2SH-P2WSH) encoding. A single witness script carries both
// spending paths:
//
//	(user AND cosigner AND lightningServer)                 // collaborative
//	OR (user AND cosigner AND older(blocksForExpiration))   // non-collaborative
//
// A lack of lightningServer signature selects the timelock branch.
type ToClientV7 struct {
	preTaprootToClient
	network *chaincfg.Params
}

// toClientV7NonCollaborativeDelay returns the relative timelock the
// non-collaborative leaf waits: to_client delay.
func toClientV7NonCollaborativeDelay() int64 {
	return delays.toClientDelay
}

// NewToClientV7 derives the participant keys to derivationPath and builds the
// output, whose non-collaborative path waits the spec's to_client delay.
// derivationPath must match m/schema:1'/recovery:1'/<change:0|external:1>/<index>.
func NewToClientV7(
	baseKeys *hd_keys.PublicKeyTriple,
	derivationPath string,
) (*ToClientV7, error) {
	keys, err := deriveParticipantECKeys(baseKeys, derivationPath)
	if err != nil {
		return nil, err
	}

	return &ToClientV7{
		preTaprootToClient: newPreTaprootToClient(
			keys.userPubKey,
			keys.cosignerPubKey,
			keys.lightningServerPubKey,
			derivationPath,
			toClientV7NonCollaborativeDelay(),
		),
		network: baseKeys.Network(),
	}, nil
}

// Address returns the P2SH-P2WSH address paying to this output.
func (o *ToClientV7) Address() (*WalletAddress, error) {
	redeemScript, err := o.redeemScript()
	if err != nil {
		return nil, err
	}

	address, err := btcutil.NewAddressScriptHash(redeemScript, o.network)
	if err != nil {
		return nil, err
	}

	return New(V7, o.path, address.EncodeAddress()), nil
}

// InputScript returns the signature script of an input spending this output:
// a single push of the redeem script (the P2WSH program).
func (o *ToClientV7) InputScript() ([]byte, error) {
	redeemScript, err := o.redeemScript()
	if err != nil {
		return nil, err
	}

	return txscript.NewScriptBuilder().
		AddData(redeemScript).
		Script()
}

// LongestToClientV7InputScript returns an input script of the maximum
// possible size. The input script is a single push of a constant-size hash,
// so any keys and timelock yield the exact size.
func LongestToClientV7InputScript() ([]byte, error) {
	sample, err := NewToClientV7(longestPublicKeyTriple(), estimationKeyPath)
	if err != nil {
		return nil, err
	}
	return sample.InputScript()
}

// LongestToClientV7CollaborativeWitness returns a witness of the maximum size
// a collaborative ToClientV7 spend can have, built with placeholder
// signatures of the longest standard ECDSA encoding.
func LongestToClientV7CollaborativeWitness() (wire.TxWitness, error) {
	// The spec's 52560 takes the widest script-number encoding a BIP68 delta can have, 3 bytes.
	sample, err := NewToClientV7(longestPublicKeyTriple(), estimationKeyPath)
	if err != nil {
		return nil, err
	}
	placeholderSignature := longestStandardECDSASignature()
	return sample.AssembleCollaborativeWitness(
		placeholderSignature, placeholderSignature, placeholderSignature,
	)
}

// LongestToClientV7NonCollaborativeWitness returns a witness of the maximum
// size a non-collaborative ToClientV7 spend can have, built with placeholder
// signatures of the longest standard ECDSA encoding.
func LongestToClientV7NonCollaborativeWitness() (wire.TxWitness, error) {
	// The spec's 52560 takes the widest script-number encoding a BIP68 delta can have, 3 bytes.
	sample, err := NewToClientV7(longestPublicKeyTriple(), estimationKeyPath)
	if err != nil {
		return nil, err
	}
	placeholderSignature := longestStandardECDSASignature()
	return sample.AssembleNonCollaborativeWitness(
		NewNonCollaborativeFullSignatures(placeholderSignature, placeholderSignature),
	)
}

// redeemScript builds the P2SH redeem script committed by this output.
func (o *ToClientV7) redeemScript() ([]byte, error) {
	witnessScript, err := o.witnessScript()
	if err != nil {
		return nil, err
	}
	// Wrap the P2WSH program (OP_0 <sha256(witnessScript)>) in P2SH for legacy compatibility.
	return createNonNativeSegwitRedeemScript(witnessScript)
}

func createNonNativeSegwitRedeemScript(witnessScript []byte) ([]byte, error) {
	witnessScriptHash := sha256.Sum256(witnessScript)

	builder := txscript.NewScriptBuilder()
	builder.AddInt64(0)
	builder.AddData(witnessScriptHash[:])

	return builder.Script()
}
