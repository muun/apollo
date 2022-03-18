package newop

import "github.com/muun/libwallet/operation"

// PaymentContext stores data required to analyze and validate an operation
type PaymentContext struct {
	FeeWindow                *FeeWindow
	NextTransactionSize      *NextTransactionSize
	ExchangeRateWindow       *ExchangeRateWindow
	PrimaryCurrency          string
	MinFeeRateInSatsPerVByte float64
	SubmarineSwap            *SubmarineSwap
}

func (c *PaymentContext) totalBalance() int64 {
	return c.NextTransactionSize.toInternalType().TotalBalance()
}

func (c *PaymentContext) toBitcoinAmount(sats int64, inputCurrency string) *BitcoinAmount {
	amount := c.ExchangeRateWindow.convert(
		NewMonetaryAmountFromSatoshis(sats),
		inputCurrency,
	)
	return &BitcoinAmount{
		InSat:             sats,
		InInputCurrency:   amount,
		InPrimaryCurrency: c.ExchangeRateWindow.convert(amount, c.PrimaryCurrency),
	}
}

func newPaymentAnalyzer(context *PaymentContext) *operation.PaymentAnalyzer {
	return operation.NewPaymentAnalyzer(
		context.FeeWindow.toInternalType(),
		context.NextTransactionSize.toInternalType(),
	)
}
