// Package model holds the rates subsystem's boundary types.
package model

import "github.com/muun/libwallet/server/api"

// ExchangeRateWindow is a snapshot of exchange rates from BTC to every currency we handle,
// as fetched from Houston.
type ExchangeRateWindow struct {
	WindowID          int64
	FetchDateInMillis int64
	Rates             map[string]float64
}

// NewExchangeRateWindow creates an ExchangeRateWindow.
func NewExchangeRateWindow(
	windowID int64,
	fetchDateInMillis int64,
	rates map[string]float64,
) ExchangeRateWindow {
	return ExchangeRateWindow{
		WindowID:          windowID,
		FetchDateInMillis: fetchDateInMillis,
		Rates:             rates,
	}
}

// FromProtoExchangeRateWindow converts the window received over gRPC. A nil window converts to
// one with no rates, which the subsystem rejects.
func FromProtoExchangeRateWindow(proto *api.ExchangeRateWindow) ExchangeRateWindow {
	return NewExchangeRateWindow(
		proto.GetWindowId(),
		proto.GetFetchDateInMillis(),
		proto.GetRates(),
	)
}

// ToProtoExchangeRateWindow converts the window to send it over gRPC.
func ToProtoExchangeRateWindow(window ExchangeRateWindow) *api.ExchangeRateWindow {
	return api.ExchangeRateWindow_builder{
		WindowId:          window.WindowID,
		FetchDateInMillis: window.FetchDateInMillis,
		Rates:             window.Rates,
	}.Build()
}
