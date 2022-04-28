package newop

import (
	"testing"

	"github.com/shopspring/decimal"
)

func TestMonetaryAmountString(t *testing.T) {

	amount := NewMonetaryAmountFromFiat("10.54", "USD")
	if amount.ValueAsString() != "10.54" {
		t.Fatalf("expected value as string to match, got %v", amount.ValueAsString())
	}

	if amount.String() != "10.54 USD" {
		t.Fatalf("expected value as string to match, got %v", amount.String())
	}
}

func TestMonetaryAmountToBitcoinAmount(t *testing.T) {

	amount := NewMonetaryAmountFromSatoshis(100_000_000)

	window := &ExchangeRateWindow{}
	window.AddRate("BTC", 1)
	window.AddRate("USD", 32_000)

	bitcoinAmount := amount.toBitcoinAmount(window, "USD")

	if bitcoinAmount.InSat != 100_000_000 {
		t.Fatalf("expected sats amount to be 100000000, got %v", bitcoinAmount.InSat)
	}

	if bitcoinAmount.InPrimaryCurrency.Currency != "USD" {
		t.Fatalf("expected converted currency to be USD, got %v", bitcoinAmount.InPrimaryCurrency.Currency)
	}

	if bitcoinAmount.InPrimaryCurrency.Value.Cmp(decimal.NewFromInt(32_000)) != 0 {
		t.Fatalf("expected converted amount to be 32000, got %v", bitcoinAmount.InInputCurrency.Value)
	}

	if bitcoinAmount.InInputCurrency.Currency != "BTC" {
		t.Fatalf("expected intput currency to be BTC, got %v", bitcoinAmount.InInputCurrency.Currency)
	}

	if bitcoinAmount.InInputCurrency.Value.Cmp(decimal.NewFromInt(1)) != 0 {
		t.Fatalf("expected converted amount to be 1, got %v", bitcoinAmount.InInputCurrency.Value)
	}

}

func TestMonetaryAmountAdd(t *testing.T) {

	a := NewMonetaryAmountFromFiat("10", "USD")
	b := NewMonetaryAmountFromFiat("20", "USD")

	sum := a.add(b)

	if sum.Currency != "USD" {
		t.Fatalf("expected converted currency to be USD, got %v", sum.Currency)
	}

	if sum.Value.Cmp(decimal.NewFromInt(30)) != 0 {
		t.Fatalf("expected converted amount to be 30, got %v", sum.Value)
	}
}
