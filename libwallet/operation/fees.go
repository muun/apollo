package operation

import (
	"errors"
	"fmt"
	"math"
)

type feeCalculator struct {
	NextTransactionSize *NextTransactionSize
	// there is a fee bump function for each unconfirmed utxo, in the same order as they appear in the NTS.
	feeBumpFunctions []*FeeBumpFunction
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
	return f.calculateFee(amountInSat, feeRateInSatsPerVByte, takeFeeFromAmount)
}

func (f *feeCalculator) calculateFee(amountInSat int64, feeRateInSatsPerVByte float64, takeFeeFromAmount bool) int64 {
	if f.NextTransactionSize == nil {
		return 0
	}

	var fee int64
	lastUnconfirmedUtxoUsedIndex := -1
	for _, sizeForAmount := range f.NextTransactionSize.SizeProgression {
		// this code assumes that sizeProgression has the same order as used when fee bump functions was generated.
		if sizeForAmount.UtxoStatus == UtxosStatusUnconfirmed {
			lastUnconfirmedUtxoUsedIndex++
		}

		var feeBumpAmount int64 = 0
		if lastUnconfirmedUtxoUsedIndex >= 0 {
			var err error
			feeBumpAmount, err = f.calculateFeeBumpAmount(lastUnconfirmedUtxoUsedIndex, feeRateInSatsPerVByte)
			if err != nil {
				// TODO: Add listener to track non-fatal error.
				fmt.Printf("Non-fatal error calculating fee bump amount: %v\n", err.Error())
			}
		}

		fee = computeFee(sizeForAmount.SizeInVByte, feeRateInSatsPerVByte, feeBumpAmount)
		if takeFeeFromAmount {
			if sizeForAmount.AmountInSat >= amountInSat {
				break // no more UTXOs needed
			}
		} else {
			if sizeForAmount.AmountInSat >= amountInSat+fee {
				break // no more UTXOs needed
			}
		}
	}
	return fee
}

func computeFee(sizeInVByte int64, feeRate float64, feeBumpAmount int64) int64 {
	return int64(math.Ceil(float64(sizeInVByte)*feeRate)) + feeBumpAmount
}

// calculateFeeBumpAmount calculates the fee needed to bump the unconfirmed ancestors of the
// transaction via child-pays-for-parent.
// If the order among unconfirmed utxos in NTS and fee bump functions is broken,
// the fee bump function related code will not work as expected.
// it handles the case when lastUnconfirmedUtxoUsedIndex is greater than feeBumpFunctions length
func (f *feeCalculator) calculateFeeBumpAmount(
	lastUnconfirmedUtxoUsedIndex int,
	feeRateInSatsPerVByte float64,
) (int64, error) {

	// If feature flag is OFF, this can be nil
	if len(f.feeBumpFunctions) == 0 {
		// Non-fatal error, sending for tracking. Fee bump amount will be 0
		return 0, errors.New("fee bump functions were not loaded")
	}

	functionIndex := f.getFeeBumpFunctionIndex(lastUnconfirmedUtxoUsedIndex)

	feeBumpAmount, err := f.feeBumpFunctions[functionIndex].GetBumpAmountForFeeRate(feeRateInSatsPerVByte)

	if err != nil {
		return 0, err
	}

	return feeBumpAmount, nil
}

func (f *feeCalculator) getFeeBumpFunctionIndex(lastUnconfirmedUtxoUsedIndex int) int {
	// We might have fewer fee bump functions than unconfirmed UTXOs; in that case,
	// we should use the last fee bump function.
	// There are no gaps in the middle, meaning the last N UTXOs will use the last fee bump function.
	// If this order is broken, the fee bump function related code will not work as expected.
	if lastUnconfirmedUtxoUsedIndex >= len(f.feeBumpFunctions) {
		lastUnconfirmedUtxoUsedIndex = len(f.feeBumpFunctions) - 1
	}
	return lastUnconfirmedUtxoUsedIndex
}
