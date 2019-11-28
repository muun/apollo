package libwallet

import (
	"github.com/pkg/errors"

	"github.com/btcsuite/btcd/chaincfg"
	"github.com/btcsuite/btcutil/hdkeychain"
)

func keyFromString(str string) (*hdkeychain.ExtendedKey, *Network, error) {

	key, err := hdkeychain.NewKeyFromString(str)
	if err != nil {
		return nil, nil, err
	}

	var params *chaincfg.Params
	if key.IsForNet(&chaincfg.MainNetParams) {
		params = &chaincfg.MainNetParams
	} else if key.IsForNet(&chaincfg.TestNet3Params) {
		params = &chaincfg.TestNet3Params
	} else if key.IsForNet(&chaincfg.RegressionNetParams) {
		params = &chaincfg.RegressionNetParams
	} else {
		return nil, nil, errors.New("this key is for an unknown network")
	}

	return key, newNetwork(params), nil
}
