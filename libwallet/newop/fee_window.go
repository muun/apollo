package newop

import "github.com/muun/libwallet/operation"

// FeeWindow holds a map of target block to fee rate for a given time
type FeeWindow struct {
	FastConfTarget   int64
	MediumConfTarget int64
	SlowConfTarget   int64
	TargetedFees     map[uint]float64
}

func (w *FeeWindow) PutTargetedFees(target int64, feeRateInSatsPerVByte float64) {
	if w.TargetedFees == nil {
		w.TargetedFees = make(map[uint]float64)
	}
	w.TargetedFees[uint(target)] = feeRateInSatsPerVByte
}

func (w *FeeWindow) GetTargetedFees(target int64) float64 {
	if w.TargetedFees == nil {
		return 0
	}
	return w.TargetedFees[uint(target)]
}

func (w *FeeWindow) nextHighestBlock(feeRate float64) int64 {
	return int64(w.toInternalType().NextHighestBlock(feeRate))
}

func (w *FeeWindow) toInternalType() *operation.FeeWindow {
	return &operation.FeeWindow{
		TargetedFees: w.TargetedFees,
	}
}
