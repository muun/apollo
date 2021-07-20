package operation

import (
	"math"
)

type feeCalculator struct {
	NextTransactionSize *NextTransactionSize
}

// Fee DOES NOT return error when amount > balance. Instead we return the fee it would take to
// spend all utxos and delegate to the caller the task of checking if that is spendable with the given
// amount. This is to avoid using go error handling.
// Consequences of this:
// - we don't check balance whatsoever
// - fee for COLLECT swap is exactly the same as normal case
func (f *feeCalculator) Fee(amountInSat int64, feeRateInSatsPerVByte float64, takeFeeFromAmount bool) int64 {
	if amountInSat == 0 {
		return 0
	}
	if takeFeeFromAmount {
		return f.feeFromAmount(amountInSat, feeRateInSatsPerVByte)
	} else {
		return f.feeFromRemainingBalance(amountInSat, feeRateInSatsPerVByte)
	}
}

func (f *feeCalculator) feeFromAmount(amountInSat int64, feeRateInSatsPerVByte float64) int64 {
	if f.NextTransactionSize == nil {
		return 0
	}

	var fee int64
	for _, sizeForAmount := range f.NextTransactionSize.SizeProgression {
		fee = computeFee(sizeForAmount.SizeInVByte, feeRateInSatsPerVByte)
		if sizeForAmount.AmountInSat >= amountInSat {
			break // no more UTXOs needed
		}
	}
	return fee
}

func (f *feeCalculator) feeFromRemainingBalance(amountInSat int64, feeRateInSatsPerVByte float64) int64 {
	if f.NextTransactionSize == nil {
		return 0
	}

	var fee int64
	for _, sizeForAmount := range f.NextTransactionSize.SizeProgression {
		fee = computeFee(sizeForAmount.SizeInVByte, feeRateInSatsPerVByte)
		if sizeForAmount.AmountInSat >= amountInSat+fee {
			break // no more UTXOs needed
		}
	}
	return fee
}

func computeFee(sizeInVByte int64, feeRate float64) int64 {
	return int64(math.Ceil(float64(sizeInVByte) * feeRate))
}
