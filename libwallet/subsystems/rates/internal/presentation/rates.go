// Package presentation implements the rates subsystem's contract.
package presentation

import (
	"context"

	"github.com/muun/libwallet/subsystems/rates/internal/domain/action"
	rates_model "github.com/muun/libwallet/subsystems/rates/presentation/model"
)

// Rates implements the rates subsystem's contract.
type Rates struct {
	loadExchangeRateWindow action.LoadExchangeRateWindowAction
	saveExchangeRateWindow action.SaveExchangeRateWindowAction
}

// NewRates creates the contract implementation.
func NewRates(
	loadExchangeRateWindow action.LoadExchangeRateWindowAction,
	saveExchangeRateWindow action.SaveExchangeRateWindowAction,
) Rates {
	return Rates{
		loadExchangeRateWindow: loadExchangeRateWindow,
		saveExchangeRateWindow: saveExchangeRateWindow,
	}
}

// LoadExchangeRateWindow returns the stored exchange rate window, or nil when none has been
// stored yet.
func (r *Rates) LoadExchangeRateWindow(
	ctx context.Context,
) (*rates_model.ExchangeRateWindow, error) {
	return r.loadExchangeRateWindow.Run(ctx)
}

// SaveExchangeRateWindow stores the given exchange rate window, replacing the previous one.
func (r *Rates) SaveExchangeRateWindow(
	ctx context.Context,
	window rates_model.ExchangeRateWindow,
) error {
	return r.saveExchangeRateWindow.Run(ctx, window)
}
