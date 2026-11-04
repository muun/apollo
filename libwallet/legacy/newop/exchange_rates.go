package newop

import (
	"github.com/shopspring/decimal"

	libwallet "github.com/muun/libwallet/legacy"
	rates_model "github.com/muun/libwallet/subsystems/rates/presentation/model"
)

// ExchangeRateWindow holds a map of exchange rates from BTC to every currency we handle
type ExchangeRateWindow struct {
	WindowId int //nolint:staticcheck // gomobile export
	rates    map[string]float64
}

func newExchangeRateWindowFromModel(window *rates_model.ExchangeRateWindow) *ExchangeRateWindow {
	return &ExchangeRateWindow{
		// WindowId stays int to preserve the existing gomobile field type. Go's int is
		// 32-bit on the 32-bit ABIs (armeabi-v7a, x86), where ids >= 2^31 would truncate —
		// same limit the binding setter already imposed when the apps passed the window in.
		WindowId: int(window.WindowID),
		rates:    window.Rates,
	}
}

func (w *ExchangeRateWindow) AddRate(
	currency string,
	rate float64,
) {
	if w.rates == nil {
		w.rates = make(map[string]float64)
	}
	w.rates[currency] = rate
}

func (w *ExchangeRateWindow) Rate(currency string) float64 {
	return w.rates[currency]
}

func (w *ExchangeRateWindow) Currencies() *libwallet.StringList {
	var currencies []string
	for key := range w.rates {
		currencies = append(currencies, key)
	}
	return libwallet.NewStringListWithElements(currencies)
}

func (w *ExchangeRateWindow) convert(amount *MonetaryAmount, currency string) *MonetaryAmount {
	fromRate := decimal.NewFromFloat(w.Rate(amount.Currency))
	toRate := decimal.NewFromFloat(w.Rate(currency))
	value := amount.Value.Div(fromRate).Mul(toRate)

	return &MonetaryAmount{Value: value, Currency: currency}
}
