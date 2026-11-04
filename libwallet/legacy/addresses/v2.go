package addresses

import (
	"github.com/btcsuite/btcd/btcutil"
	"github.com/btcsuite/btcd/btcutil/hdkeychain"
	"github.com/btcsuite/btcd/chaincfg"
	"github.com/btcsuite/btcd/txscript"
	goerr "github.com/go-errors/errors"
	"github.com/pkg/errors"

	walletaddresses "github.com/muun/libwallet/base/domain/addresses"
)

func CreateAddressV2(
	userKey, cosignerKey *hdkeychain.ExtendedKey,
	path string,
	network *chaincfg.Params,
) (*walletaddresses.WalletAddress, error) {

	script, err := CreateRedeemScriptV2(userKey, cosignerKey, network)
	if err != nil {
		return nil, goerr.Errorf("failed to generate redeem script v2: %w", err)
	}

	address, err := btcutil.NewAddressScriptHash(script, network)
	if err != nil {
		return nil, goerr.Errorf("failed to generate multisig address: %w", err)
	}

	return walletaddresses.New(V2, path, address.EncodeAddress()), nil
}

func CreateRedeemScriptV2(
	userKey, cosignerKey *hdkeychain.ExtendedKey,
	network *chaincfg.Params,
) ([]byte, error) {
	return createMultisigRedeemScript(userKey, cosignerKey, network)
}

func createMultisigRedeemScript(
	userKey, cosignerKey *hdkeychain.ExtendedKey,
	network *chaincfg.Params,
) ([]byte, error) {
	userPublicKey, err := userKey.ECPubKey()
	if err != nil {
		return nil, err
	}
	userAddress, err := btcutil.NewAddressPubKey(userPublicKey.SerializeCompressed(), network)
	if err != nil {
		return nil, errors.Wrapf(err, "failed to generate address for user")
	}

	cosignerPublicKey, err := cosignerKey.ECPubKey()
	if err != nil {
		return nil, err
	}
	WalletAddress, err := btcutil.NewAddressPubKey(cosignerPublicKey.SerializeCompressed(), network)
	if err != nil {
		return nil, errors.Wrapf(err, "failed to generate address for cosigner")
	}

	return txscript.MultiSigScript([]*btcutil.AddressPubKey{
		userAddress,
		WalletAddress,
	}, 2)
}
