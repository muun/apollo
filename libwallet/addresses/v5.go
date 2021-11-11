package addresses

import (
	"fmt"

	"github.com/btcsuite/btcd/chaincfg"
	"github.com/btcsuite/btcutil/hdkeychain"
	"github.com/muun/libwallet/btcsuitew/btcutilw"
	"github.com/muun/libwallet/musig"
)

// CreateAddressV5 returns a P2TR WalletAddress using Musig with the signing and cosigning keys.
func CreateAddressV5(userKey, muunKey *hdkeychain.ExtendedKey, path string, network *chaincfg.Params) (*WalletAddress, error) {
	witnessProgram, err := CreateWitnessScriptV5(userKey, muunKey)
	if err != nil {
		return nil, fmt.Errorf("failed to generate witness script v5: %w", err)
	}

	address, err := btcutilw.NewAddressTaprootKey(witnessProgram, network)
	if err != nil {
		return nil, err
	}

	return &WalletAddress{
		address:        address.EncodeAddress(),
		version:        V5,
		derivationPath: path,
	}, nil
}

func CreateWitnessScriptV5(userKey, muunKey *hdkeychain.ExtendedKey) ([]byte, error) {
	userPublicKey, err := userKey.ECPubKey()
	if err != nil {
		return nil, err
	}

	muunPublicKey, err := muunKey.ECPubKey()
	if err != nil {
		return nil, err
	}

	combined, err := musig.CombinePubKeysWithTweak(userPublicKey, muunPublicKey, nil)
	if err != nil {
		return nil, err
	}

	xOnlyCombined := combined.SerializeCompressed()[1:]

	return xOnlyCombined, nil
}
