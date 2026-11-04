package rates

import (
	"github.com/muun/libwallet/base/data/storage"
	"github.com/muun/libwallet/subsystems/rates/internal/data/repository"
	"github.com/muun/libwallet/subsystems/rates/internal/domain/action"
	"github.com/muun/libwallet/subsystems/rates/internal/presentation"
)

// Rates re-exports the contract implementation for libwallet_init.
type Rates = presentation.Rates

// NewRates creates the rates subsystem backed by the given key-value storage.
func NewRates(kv *storage.KeyValueStorage) Rates {
	windowRepository := repository.NewExchangeRateWindowRepository(kv)

	return presentation.NewRates(
		action.NewLoadExchangeRateWindowAction(windowRepository),
		action.NewSaveExchangeRateWindowAction(windowRepository),
	)
}
