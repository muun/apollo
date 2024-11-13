package addresses

import (
	"fmt"

	"github.com/btcsuite/btcd/btcutil/hdkeychain"
	"github.com/btcsuite/btcd/chaincfg"
	"github.com/muun/libwallet/btcsuitew/btcutilw"
	"github.com/muun/libwallet/musig"
)

// CreateAddressV6 returns a P2TR WalletAddress using Musig2v100 with the signing and cosigning keys.
func CreateAddressV6(userKey, muunKey *hdkeychain.ExtendedKey, path string, network *chaincfg.Params) (*WalletAddress, error) {
	witnessProgram, err := CreateWitnessScriptV6(userKey, muunKey)
	if err != nil {
		return nil, fmt.Errorf("failed to generate witness script v5: %w", err)
	}

	address, err := btcutilw.NewAddressTaprootKey(witnessProgram, network)
	if err != nil {
		return nil, err
	}

	return &WalletAddress{
		address:        address.EncodeAddress(),
		version:        V6,
		derivationPath: path,
	}, nil
}

func CreateWitnessScriptV6(userKey, muunKey *hdkeychain.ExtendedKey) ([]byte, error) {
	userPublicKey, err := userKey.ECPubKey()
	if err != nil {
		return nil, fmt.Errorf("error getting pub key: %w", err)
	}
	muunPublicKey, err := muunKey.ECPubKey()
	if err != nil {
		return nil, fmt.Errorf("error getting pub key: %w", err)
	}

	pubKeys := [][]byte{
		userPublicKey.SerializeCompressed(),
		muunPublicKey.SerializeCompressed(),
	}

	tweak := musig.KeySpendOnlyTweak()

	aggregateKey, err := musig.Musig2CombinePubKeysWithTweak(musig.Musig2v100, pubKeys, tweak)
	if err != nil {
		return nil, fmt.Errorf("error combining keys: %w", err)
	}

	xOnlyCombined := aggregateKey.FinalKey.SerializeCompressed()[1:]

	return xOnlyCombined, nil
}
