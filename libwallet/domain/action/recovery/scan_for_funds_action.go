package recovery

import (
	"fmt"
	"github.com/muun/libwallet"
	"github.com/muun/libwallet/data/keys"
	"github.com/muun/libwallet/electrum"
	"github.com/muun/libwallet/scanner"
	"log/slog"
)

type ScanForFundsAction struct {
	keyProvider      keys.KeyProvider
	electrumProvider *electrum.ServerProvider
	network          *libwallet.Network
}

func NewScanForFundsAction(keyProvider keys.KeyProvider, electrumProvider *electrum.ServerProvider, network *libwallet.Network) *ScanForFundsAction {
	return &ScanForFundsAction{
		keyProvider:      keyProvider,
		electrumProvider: electrumProvider,
		network:          network,
	}
}

func (action *ScanForFundsAction) Run(logger *slog.Logger) (<-chan *scanner.Report, error) {
	addresses, err := generateAddresses(action.keyProvider)
	if err != nil {
		return nil, err
	}

	const electrumPoolSize = 8
	connectionPool := electrum.NewPool(electrumPoolSize, true, logger)

	utxoScanner := scanner.NewScanner(connectionPool, action.electrumProvider, action.network.ToParams())

	return utxoScanner.Scan(addresses), nil
}

// generates a list of addresses to recover
func generateAddresses(keyProvider keys.KeyProvider) (chan libwallet.MuunAddress, error) {
	userPubKey, err := keyProvider.UserPublicKey()
	if err != nil {
		return nil, err
	}

	muunKey, err := keyProvider.MuunPublicKey()
	if err != nil {
		return nil, err
	}

	addrGen := scanner.NewAddressGenerator(userPubKey, muunKey, false)

	maxIndex := keyProvider.MaxDerivedIndex()
	if maxIndex == 0 {
		return nil, fmt.Errorf("cannot generate 0 addresses")
	}
	addresses := addrGen.Stream(int64(maxIndex))

	return addresses, nil
}
