package newop

import (
	"testing"

	"github.com/shopspring/decimal"
)

func TestExchangeWindowConvert(t *testing.T) {

	window := &ExchangeRateWindow{}
	window.AddRate("BTC", 1)
	window.AddRate("USD", 32_000)

	amount := NewMonetaryAmountFromSatoshis(100_000_000)

	converted := window.convert(amount, "USD")

	if converted.Currency != "USD" {
		t.Fatalf("expected converted currency to be USD, got %v", converted.Currency)
	}

	if converted.Value.Cmp(decimal.NewFromInt(32_000)) != 0 {
		t.Fatalf("expected converted amount to be 32000, got %v", converted.Value)
	}

}
