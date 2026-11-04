package addresses

import (
	"github.com/btcsuite/btcd/btcutil"
	"github.com/btcsuite/btcd/btcutil/hdkeychain"
	"github.com/btcsuite/btcd/chaincfg"

	walletaddresses "github.com/muun/libwallet/base/domain/addresses"
)

// CreateAddressV1 returns a P2PKH WalletAddress from a publicKey for use in TransactionSchemeV1
func CreateAddressV1(
	userKey *hdkeychain.ExtendedKey,
	path string,
	network *chaincfg.Params,
) (*walletaddresses.WalletAddress, error) {
	pubKey, err := userKey.ECPubKey()
	if err != nil {
		return nil, err
	}
	address, err := btcutil.NewAddressPubKey(pubKey.SerializeCompressed(), network)
	if err != nil {
		return nil, err
	}
	return walletaddresses.New(V1, path, address.EncodeAddress()), nil
}
