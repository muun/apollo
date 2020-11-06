package addresses

import (
	"github.com/btcsuite/btcd/chaincfg"
	"github.com/btcsuite/btcutil"
	"github.com/btcsuite/btcutil/hdkeychain"
)

// CreateAddressV1 returns a P2PKH WalletAddress from a publicKey for use in TransactionSchemeV1
func CreateAddressV1(userKey *hdkeychain.ExtendedKey, path string, network *chaincfg.Params) (*WalletAddress, error) {
	pubKey, err := userKey.ECPubKey()
	if err != nil {
		return nil, err
	}
	address, err := btcutil.NewAddressPubKey(pubKey.SerializeCompressed(), network)
	if err != nil {
		return nil, err
	}
	return &WalletAddress{
		address:        address.EncodeAddress(),
		version:        V1,
		derivationPath: path,
	}, nil
}
