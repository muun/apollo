// Package rates holds the exchange rate domain model.
package model

import (
	"github.com/go-errors/errors"

	"github.com/muun/libwallet/platform/preconditions"
)

// ExchangeRateWindow holds a snapshot of exchange rates from BTC to every currency we handle,
// as fetched from Houston.
type ExchangeRateWindow struct {
	WindowID          int64
	FetchDateInMillis int64
	Rates             map[string]float64
}

// NewExchangeRateWindow creates an ExchangeRateWindow. Windows that come from outside libwallet
// must pass ValidateExchangeRateWindow first.
func NewExchangeRateWindow(
	windowID int64,
	fetchDateInMillis int64,
	rates map[string]float64,
) *ExchangeRateWindow {
	err := ValidateExchangeRateWindow(windowID, fetchDateInMillis, rates)
	preconditions.CheckStatef(err == nil, "invalid exchange rate window: %v", err)

	return &ExchangeRateWindow{
		WindowID:          windowID,
		FetchDateInMillis: fetchDateInMillis,
		Rates:             rates,
	}
}

// ValidateExchangeRateWindow fails when the window has no rates or a non-positive id or fetch
// date. Windows come from outside libwallet, so invalid ones are reported as errors rather than
// panics.
func ValidateExchangeRateWindow(
	windowID int64,
	fetchDateInMillis int64,
	rates map[string]float64,
) error {
	if len(rates) == 0 {
		return errors.New("exchange rate window has no rates")
	}
	if windowID <= 0 {
		return errors.Errorf("exchange rate window has non-positive windowId %d", windowID)
	}
	if fetchDateInMillis <= 0 {
		return errors.Errorf(
			"exchange rate window has non-positive fetchDateInMillis %d", fetchDateInMillis,
		)
	}
	return nil
}
