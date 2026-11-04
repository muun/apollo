package addresses

import (
	"github.com/btcsuite/btcd/btcutil/hdkeychain"
	"github.com/btcsuite/btcd/chaincfg"
	"github.com/go-errors/errors"

	walletaddresses "github.com/muun/libwallet/base/domain/addresses"
	"github.com/muun/libwallet/base/domain/cryptography/musig"
	"github.com/muun/libwallet/legacy/btcsuitew/btcutilw"
)

// CreateAddressV5 returns a P2TR WalletAddress using Musig2v040Muun with the signing and cosigning
// keys.
func CreateAddressV5(
	userKey, cosignerKey *hdkeychain.ExtendedKey,
	path string,
	network *chaincfg.Params,
) (*walletaddresses.WalletAddress, error) {
	witnessProgram, err := CreateWitnessScriptV5(userKey, cosignerKey)
	if err != nil {
		return nil, errors.Errorf("failed to generate witness script v5: %w", err)
	}

	address, err := btcutilw.NewAddressTaprootKey(witnessProgram, network)
	if err != nil {
		return nil, err
	}

	return walletaddresses.New(V5, path, address.EncodeAddress()), nil
}

func CreateWitnessScriptV5(userKey, cosignerKey *hdkeychain.ExtendedKey) ([]byte, error) {
	userPublicKey, err := userKey.ECPubKey()
	if err != nil {
		return nil, errors.Errorf("error getting pub key: %w", err)
	}
	cosignerPublicKey, err := cosignerKey.ECPubKey()
	if err != nil {
		return nil, errors.Errorf("error getting pub key: %w", err)
	}

	pubKeys := [][]byte{
		userPublicKey.SerializeCompressed(),
		cosignerPublicKey.SerializeCompressed(),
	}

	tweak := musig.KeySpendOnlyTweak()

	aggregateKey, err := musig.Musig2CombinePubKeysWithTweak(musig.Musig2v040Muun, pubKeys, tweak)
	if err != nil {
		return nil, errors.Errorf("error combining keys: %w", err)
	}

	xOnlyCombined := aggregateKey.FinalKey.SerializeCompressed()[1:]

	return xOnlyCombined, nil
}
