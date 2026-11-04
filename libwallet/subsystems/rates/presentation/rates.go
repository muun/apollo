// Package presentation defines the rates subsystem's external contract.
package presentation

import (
	"context"

	rates_model "github.com/muun/libwallet/subsystems/rates/presentation/model"
)

// Rates is the rates subsystem's contract.
type Rates interface {
	// LoadExchangeRateWindow returns the stored exchange rate window, or nil when none has been
	// stored yet.
	LoadExchangeRateWindow(ctx context.Context) (*rates_model.ExchangeRateWindow, error)

	// SaveExchangeRateWindow stores the given exchange rate window, replacing the previous one.
	// It fails with rates_model.InvalidExchangeRateWindowError when the window has no rates or a
	// non-positive id or fetch date.
	SaveExchangeRateWindow(ctx context.Context, window rates_model.ExchangeRateWindow) error
}
