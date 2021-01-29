package addresses

import (
	"fmt"

	"github.com/btcsuite/btcd/chaincfg"
	"github.com/btcsuite/btcd/txscript"
	"github.com/btcsuite/btcutil"
	"github.com/btcsuite/btcutil/hdkeychain"
	"github.com/pkg/errors"
)

func CreateAddressV2(userKey, muunKey *hdkeychain.ExtendedKey, path string, network *chaincfg.Params) (*WalletAddress, error) {

	script, err := CreateRedeemScriptV2(userKey, muunKey, network)
	if err != nil {
		return nil, fmt.Errorf("failed to generate redeem script v2: %w", err)
	}

	address, err := btcutil.NewAddressScriptHash(script, network)
	if err != nil {
		return nil, fmt.Errorf("failed to generate multisig address: %w", err)
	}

	return &WalletAddress{
		address:        address.EncodeAddress(),
		version:        V2,
		derivationPath: path,
	}, nil
}

func CreateRedeemScriptV2(userKey, muunKey *hdkeychain.ExtendedKey, network *chaincfg.Params) ([]byte, error) {
	return createMultisigRedeemScript(userKey, muunKey, network)
}

func createMultisigRedeemScript(userKey, muunKey *hdkeychain.ExtendedKey, network *chaincfg.Params) ([]byte, error) {
	userPublicKey, err := userKey.ECPubKey()
	if err != nil {
		return nil, err
	}
	userAddress, err := btcutil.NewAddressPubKey(userPublicKey.SerializeCompressed(), network)
	if err != nil {
		return nil, errors.Wrapf(err, "failed to generate address for user")
	}

	muunPublicKey, err := muunKey.ECPubKey()
	if err != nil {
		return nil, err
	}
	WalletAddress, err := btcutil.NewAddressPubKey(muunPublicKey.SerializeCompressed(), network)
	if err != nil {
		return nil, errors.Wrapf(err, "failed to generate address for muun")
	}

	return txscript.MultiSigScript([]*btcutil.AddressPubKey{
		userAddress,
		WalletAddress,
	}, 2)
}
