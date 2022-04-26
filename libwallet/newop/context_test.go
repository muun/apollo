package newop

import (
	"github.com/shopspring/decimal"
	"testing"
)

var testPaymentContext = createTestPaymentContext()

func createTestPaymentContext() *PaymentContext {
	var context = &PaymentContext{
		NextTransactionSize: &NextTransactionSize{
			ExpectedDebtInSat: 10_000,
		},
		ExchangeRateWindow: &ExchangeRateWindow{
			rates: make(map[string]float64),
		},
		FeeWindow:                &FeeWindow{},
		PrimaryCurrency:          "BTC",
		MinFeeRateInSatsPerVByte: 1.0,
	}
	context.NextTransactionSize.AddSizeForAmount(&SizeForAmount{
		AmountInSat: 100_000_000,
		SizeInVByte: 240,
	})

	context.ExchangeRateWindow.AddRate("BTC", 1)
	context.ExchangeRateWindow.AddRate("USD", 32_000)

	return context
}

func TestPaymentContextTotalBalance(t *testing.T) {

	totalBalance := testPaymentContext.totalBalance()

	if totalBalance != 99_990_000 {
		t.Fatalf("expected totalBalance to be 90_000, got %v", totalBalance)
	}
}

func TestPaymentContextToBitcoinAmount(t *testing.T) {

	btcAmount := testPaymentContext.toBitcoinAmount(100_000, "USD")

	if btcAmount.InSat != 100_000 {
		t.Fatalf("expected bitcoin amount in sats to remain unchanged and  be 100_000, got %v", btcAmount.InSat)
	}

	if btcAmount.InInputCurrency.Currency != "USD" {
		t.Fatalf("expected bitcoin amount input currency to be USD, got %v", btcAmount.InInputCurrency.Currency)
	}

	if btcAmount.InInputCurrency.Value.Cmp(decimal.NewFromInt(32)) != 0 {
		t.Fatalf("expected converted amount to be 32, got %v", btcAmount.InInputCurrency.Value)
	}

	if btcAmount.InPrimaryCurrency.Currency != "BTC" {
		t.Fatalf("expected bitcoin amount primary currency to be BTC, got %v", btcAmount.InPrimaryCurrency.Currency)
	}

	if btcAmount.InPrimaryCurrency.Value.Cmp(decimal.NewFromFloat(0.001)) != 0 {
		t.Fatalf("expected amount in primary currency to be 0.001, got %v", btcAmount.InPrimaryCurrency.Value)
	}
}
