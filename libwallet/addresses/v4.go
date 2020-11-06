package addresses

import (
	"crypto/sha256"

	"github.com/btcsuite/btcd/chaincfg"
	"github.com/btcsuite/btcutil"
	"github.com/btcsuite/btcutil/hdkeychain"
	"github.com/pkg/errors"
)

// CreateAddressV4 returns a P2WSH WalletAddress from a user HD-pubkey and a Muun co-signing HD-pubkey.
func CreateAddressV4(userKey, muunKey *hdkeychain.ExtendedKey, path string, network *chaincfg.Params) (*WalletAddress, error) {

	witnessScript, err := CreateWitnessScriptV4(userKey, muunKey, network)
	if err != nil {
		return nil, errors.Wrapf(err, "failed to generate witness script v4")
	}
	witnessScript256 := sha256.Sum256(witnessScript)

	address, err := btcutil.NewAddressWitnessScriptHash(witnessScript256[:], network)
	if err != nil {
		return nil, err
	}

	return &WalletAddress{
		address:        address.EncodeAddress(),
		version:        V4,
		derivationPath: path,
	}, nil
}

func CreateWitnessScriptV4(userKey, muunKey *hdkeychain.ExtendedKey, network *chaincfg.Params) ([]byte, error) {
	// createMultisigRedeemScript creates a valid script for V2, V3 and V4 schemes
	return createMultisigRedeemScript(userKey, muunKey, network)
}
