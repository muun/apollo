package libwallet

import (
	"github.com/btcsuite/btcd/chaincfg"
)

// Network has the parameters for operating in a given Bitcoin network
type Network struct {
	network *chaincfg.Params
}

func newNetwork(params *chaincfg.Params) *Network {
	return &Network{network: params}
}

// Mainnet returns an instance of the Bitcoin Main Network
func Mainnet() *Network {
	return &Network{network: &chaincfg.MainNetParams}
}

// Testnet returns an instance of the Bitcoin Test Network
func Testnet() *Network {
	return &Network{network: &chaincfg.TestNet3Params}
}

// Regtest returns an instance of the Bitcoin Regression Network
func Regtest() *Network {
	return &Network{network: &chaincfg.RegressionNetParams}
}

// Name returns the Network's name
func (n *Network) Name() string {
	return n.network.Name
}
