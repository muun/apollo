package action

import (
	"log/slog"

	"github.com/go-errors/errors"

	"github.com/muun/libwallet/app_provided_data/adapted"
	"github.com/muun/libwallet/base/data/electrum"
	libwallet "github.com/muun/libwallet/legacy"
	"github.com/muun/libwallet/legacy/scanner"
)

type ScanForFundsAction interface {
	Run(logger *slog.Logger) (<-chan *scanner.Report, error)
}

type scanForFundsAction struct {
	keyProvider      adapted.KeyProvider
	electrumProvider *electrum.ServerProvider
	network          *libwallet.Network
}

func NewScanForFundsAction(
	keyProvider adapted.KeyProvider,
	electrumProvider *electrum.ServerProvider,
	network *libwallet.Network,
) ScanForFundsAction {
	return &scanForFundsAction{
		keyProvider:      keyProvider,
		electrumProvider: electrumProvider,
		network:          network,
	}
}

func (action *scanForFundsAction) Run(logger *slog.Logger) (<-chan *scanner.Report, error) {
	addresses, err := generateAddresses(action.keyProvider)
	if err != nil {
		return nil, err
	}

	const electrumPoolSize = 8
	connectionPool := electrum.NewPool(electrumPoolSize, true, logger)

	utxoScanner := scanner.NewScanner(
		connectionPool,
		action.electrumProvider,
		action.network.ToParams(),
	)

	return utxoScanner.Scan(addresses), nil
}

// generates a list of addresses to recover
func generateAddresses(keyProvider adapted.KeyProvider) (chan libwallet.MuunAddress, error) {
	userPubKey, err := keyProvider.UserPublicKey()
	if err != nil {
		return nil, err
	}

	cosignerKey, err := keyProvider.CosignerPublicKey()
	if err != nil {
		return nil, err
	}

	addrGen := scanner.NewAddressGenerator(userPubKey, cosignerKey, false)

	maxIndex := keyProvider.MaxDerivedIndex()
	if maxIndex == 0 {
		return nil, errors.Errorf("cannot generate 0 addresses")
	}
	addresses := addrGen.Stream(int64(maxIndex))

	return addresses, nil
}
