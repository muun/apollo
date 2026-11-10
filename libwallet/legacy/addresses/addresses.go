package addresses

import (
	"github.com/btcsuite/btcd/btcutil/hdkeychain"
	"github.com/btcsuite/btcd/chaincfg"
	"github.com/go-errors/errors"

	walletaddresses "github.com/muun/libwallet/base/domain/addresses"
	"github.com/muun/libwallet/base/domain/cryptography/musig"
)

const (
	V1              = 1
	V2              = 2
	V3              = 3
	V4              = 4
	V5              = 5
	V6              = 6
	SubmarineSwapV1 = 101
	SubmarineSwapV2 = 102
	IncomingSwap    = 201
)

func Create(
	version int,
	userKey, cosignerKey *hdkeychain.ExtendedKey,
	path string,
	network *chaincfg.Params,
) (*walletaddresses.WalletAddress, error) {
	switch version {
	case V1:
		return CreateAddressV1(userKey, path, network)
	case V2:
		return CreateAddressV2(userKey, cosignerKey, path, network)
	case V3:
		return CreateAddressV3(userKey, cosignerKey, path, network)
	case V4:
		return CreateAddressV4(userKey, cosignerKey, path, network)
	case V5:
		return CreateAddressV5(userKey, cosignerKey, path, network)
	case V6:
		return CreateAddressV6(userKey, cosignerKey, path, network)
	default:
		return nil, errors.Errorf("unknown or unsupported version %v", version)
	}
}

func MusigVersionForAddress(addressVersion int) musig.MusigVersion {
	switch addressVersion {
	case V1, V2, V3, V4, V5, SubmarineSwapV1, SubmarineSwapV2, IncomingSwap:
		return musig.Musig2v040Muun
	default:
		return musig.Musig2v100
	}
}
