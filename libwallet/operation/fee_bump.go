package operation

import (
	"errors"
	"time"
)

type FeeBumpFunctionSet struct {
	CreatedAt        time.Time
	UUID             string
	RefreshPolicy    string
	FeeBumpFunctions []*FeeBumpFunction
}

func (fbs *FeeBumpFunctionSet) GetSecondsSinceLastUpdate() int64 {
	return int64(time.Since(fbs.CreatedAt).Seconds())
}

type FeeBumpFunction struct {
	// it is provided in order by the backend
	PartialLinearFunctions []*PartialLinearFunction
}

type PartialLinearFunction struct {
	LeftClosedEndpoint float64
	RightOpenEndpoint  float64
	Slope              float64
	Intercept          float64
}

func (pf *PartialLinearFunction) evaluate(feeRateInSatsPerVByte float64) (float64, error) {
	if feeRateInSatsPerVByte >= pf.LeftClosedEndpoint && feeRateInSatsPerVByte < pf.RightOpenEndpoint {
		return pf.Slope*feeRateInSatsPerVByte + pf.Intercept, nil
	}
	return 0, errors.New("fee rate does not belong to this interval")
}

func (fb *FeeBumpFunction) getPartialLinearFunctionForFeeRate(
	feeRateInSatsPerVByte float64,
) (*PartialLinearFunction, error) {
	for _, partialLinearFunction := range fb.PartialLinearFunctions {
		if feeRateInSatsPerVByte >= partialLinearFunction.LeftClosedEndpoint && feeRateInSatsPerVByte < partialLinearFunction.RightOpenEndpoint {
			return partialLinearFunction, nil
		}
	}
	return nil, errors.New("there is no function with this fee rate interval")
}

// GetBumpAmountForFeeRate assumes that there is no overlap between the intervals.
func (f *FeeBumpFunction) GetBumpAmountForFeeRate(feeRateInSatsPerVByte float64) (int64, error) {
	if f.PartialLinearFunctions == nil {
		return 0, errors.New("fee bump function does not exist")
	}

	partialLinearFunction, err := f.getPartialLinearFunctionForFeeRate(feeRateInSatsPerVByte)

	if err != nil {
		return 0, err
	}

	bumpAmount, err := partialLinearFunction.evaluate(feeRateInSatsPerVByte)

	// interval range is checked again inside evaluate function
	if err != nil {
		return 0, err
	}

	return int64(bumpAmount), nil
}
