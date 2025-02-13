package newop

import "github.com/muun/libwallet/operation"

// InitialPaymentContext receives operation data provided by the native platform.
type InitialPaymentContext struct {
	FeeWindow                *FeeWindow
	NextTransactionSize      *NextTransactionSize
	ExchangeRateWindow       *ExchangeRateWindow
	PrimaryCurrency          string
	MinFeeRateInSatsPerVByte float64
	SubmarineSwap            *SubmarineSwap
}

// PaymentContext stores data required to analyze and validate an operation
// It comprises InitialPaymentContext with data from native apps,
// adding properties loaded inside Libwallet.
type PaymentContext struct {
	//****** InitialPaymentContext ******
	// Copied from InitialPaymentContext to avoid awful nested hierarchy
	// on native apps.
	FeeWindow                *FeeWindow
	NextTransactionSize      *NextTransactionSize
	ExchangeRateWindow       *ExchangeRateWindow
	PrimaryCurrency          string
	MinFeeRateInSatsPerVByte float64
	SubmarineSwap            *SubmarineSwap
	//***********************************

	feeBumpFunctionSet *operation.FeeBumpFunctionSet
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

func (c *PaymentContext) getFeeBumpFunctions() []*operation.FeeBumpFunction {
	if c.feeBumpFunctionSet == nil {
		return nil
	}
	return c.feeBumpFunctionSet.FeeBumpFunctions
}

func newPaymentAnalyzer(context *PaymentContext) *operation.PaymentAnalyzer {
	return operation.NewPaymentAnalyzer(
		context.FeeWindow.toInternalType(),
		context.NextTransactionSize.toInternalType(),
		context.getFeeBumpFunctions(),
	)
}

func (ipc *InitialPaymentContext) newPaymentContext(feeBumpFunctionSet *operation.FeeBumpFunctionSet) *PaymentContext {
	return &PaymentContext{
		FeeWindow:                ipc.FeeWindow,
		NextTransactionSize:      ipc.NextTransactionSize,
		ExchangeRateWindow:       ipc.ExchangeRateWindow,
		PrimaryCurrency:          ipc.PrimaryCurrency,
		MinFeeRateInSatsPerVByte: ipc.MinFeeRateInSatsPerVByte,
		SubmarineSwap:            ipc.SubmarineSwap,
		feeBumpFunctionSet:       feeBumpFunctionSet,
	}
}
