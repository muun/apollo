package operation

import (
	"fmt"
	"math"
)

const swapV2ConfTarget = 250 // Approx 2 days

type FeeWindow struct {
	TargetedFees map[uint]float64
}

// SwapFeeRate gets the appropriate fee rate for a given swap (depends on confirmations needed).
// Useful method for when swap doesn't have a fixed amount (e.g AmountLessInvoices + use all funds).
func (f *FeeWindow) SwapFeeRate(confirmationsNeeded uint) (float64, error) {
	if confirmationsNeeded == 0 {
		return f.MinimumFeeRate(swapV2ConfTarget)
	}

	return f.FastestFeeRate(), nil
}

// MinimumFeeRate gets the minimum available fee rate that will hit a given confirmation target. We
// make no guesses (no averages or interpolations), so we might overshoot the fee if data is too
// sparse.
// Note: the lower the confirmation target, the faster the tx will confirm, and greater the
// fee(rate) will be.
func (f *FeeWindow) MinimumFeeRate(confirmationTarget uint) (float64, error) {

	if confirmationTarget <= 0 {
		return 0, fmt.Errorf("can't get feeRate. Expected positive confirmation target, got %v", confirmationTarget)
	}

	// Walk the available targets backwards, finding the highest target below the given one:
	for closestTarget := confirmationTarget; closestTarget > 0; closestTarget-- {
		if feeRate, containsKey := f.TargetedFees[closestTarget]; containsKey {
			// Found! This is the lowest fee rate that hits the given target.
			return feeRate, nil
		}
	}

	// No result? This is odd, but not illogical. It means *all* of our available targets
	// are above the requested one. Let's use the fastest:
	return f.FastestFeeRate(), nil
}

// FastestFeeRate gets the fastest fee rate, in satoshis per weight unit.
func (f *FeeWindow) FastestFeeRate() float64 {

	var lowestTarget uint = math.MaxUint32
	for k := range f.TargetedFees {
		if k < lowestTarget {
			lowestTarget = k
		}
	}

	return f.TargetedFees[lowestTarget]
}

// NextHighestBlock finds the next highest confirmation/block target for a certain feeRate. Let me
// explain, we have a map that associates a conf-target with a fee rate. Now we want to know
// associate a conf-target with a given fee rate. We want the NEXT conf-target as we usually want
// this data for predictions or estimations and this makes the predictions for the fee rate to
// "fall on the correct side" (e.g when estimating max time to confirmation for a given fee rate).
// Note: code is not our best work of art. The target < next comparison is to account for our
// TargetedFees map not necessarily being sorted.
func (f *FeeWindow) NextHighestBlock(feeRate float64) uint {
	next := uint(math.MaxUint32)
	for target, rate := range f.TargetedFees {
		if rate <= feeRate && target < next {
			next = target
		}
	}
	if next == math.MaxUint32 {
		return 0 // 0 is a not valid targetBlock, we use it to signal target not found
	}
	return next
}
