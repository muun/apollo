package addresses

import (
	"fmt"

	"github.com/btcsuite/btcd/chaincfg"
	"github.com/btcsuite/btcutil/hdkeychain"
)

const (
	V1              = 1
	V2              = 2
	V3              = 3
	V4              = 4
	V5              = 5
	SubmarineSwapV1 = 101
	SubmarineSwapV2 = 102
	IncomingSwap    = 201
)

type WalletAddress struct {
	version        int
	derivationPath string
	address        string
}

func New(version int, derivationPath string, address string) *WalletAddress {
	return &WalletAddress{
		version:        version,
		derivationPath: derivationPath,
		address:        address,
	}
}

func Create(version int, userKey, muunKey *hdkeychain.ExtendedKey, path string, network *chaincfg.Params) (*WalletAddress, error) {
	switch version {
	case V1:
		return CreateAddressV1(userKey, path, network)
	case V2:
		return CreateAddressV2(userKey, muunKey, path, network)
	case V3:
		return CreateAddressV3(userKey, muunKey, path, network)
	case V4:
		return CreateAddressV4(userKey, muunKey, path, network)
	case V5:
		return CreateAddressV5(userKey, muunKey, path, network)
	default:
		return nil, fmt.Errorf("unknown or unsupported version %v", version)
	}
}

func (a *WalletAddress) Version() int {
	return a.version
}

func (a *WalletAddress) DerivationPath() string {
	return a.derivationPath
}

func (a *WalletAddress) Address() string {
	return a.address
}
