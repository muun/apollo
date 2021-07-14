package operation

import (
	"fmt"
	"math"
)

const swapV2ConfTarget = 250 // Approx 2 days

// TODO: we should make FeeWindow enforce a non empty Map of TargetedFees via constructor
type FeeWindow struct {
	TargetedFees map[uint]float64
}

// Get the appropriate fee rate for a given swap (depends on confirmations needed). Useful
// method for when swap doesn't have a fixed amount (e.g AmountLessInvoices + use all funds).
func (f *FeeWindow) SwapFeeRate(confirmationsNeeded uint) (float64, error) {
	if confirmationsNeeded == 0 {
		return f.minimumFeeRate(swapV2ConfTarget)
	}

	return f.fastestFeeRate(), nil
}

// Get the minimum available fee rate that will hit a given confirmation target. We make no
// guesses (no averages or interpolations), so we might overshoot the fee if data is too sparse.
func (f *FeeWindow) minimumFeeRate(confirmationTarget uint) (float64, error) {

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
	return f.fastestFeeRate(), nil
}

// Get the fastest fee rate, in satoshis per weight unit.
func (f *FeeWindow) fastestFeeRate() float64 {

	var lowestTarget uint = math.MaxUint32
	for k := range f.TargetedFees {
		if k < lowestTarget {
			lowestTarget = k
		}
	}

	return f.TargetedFees[lowestTarget]
}
