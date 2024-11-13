package operation

import (
	"math"
	"testing"
)

func TestPartialLinearFunction(t *testing.T) {
	testCases := []struct {
		desc                  string
		partialLinearFunction *PartialLinearFunction
		feeRateInSatsPerVByte float64
		bumpAmountExpected    float64
		err                   bool
	}{
		{
			desc: "evaluate bump amount inside interval",
			partialLinearFunction: &PartialLinearFunction{
				LeftClosedEndpoint: 0,
				RightOpenEndpoint:  math.Inf(1),
				Slope:              2,
				Intercept:          100,
			},
			feeRateInSatsPerVByte: 100,
			bumpAmountExpected:    300,
			err:                   false,
		},
		{
			desc: "evaluate bump amount outside interval",
			partialLinearFunction: &PartialLinearFunction{
				LeftClosedEndpoint: 0,
				RightOpenEndpoint:  100,
				Slope:              2,
				Intercept:          100,
			},
			feeRateInSatsPerVByte: 200,
			bumpAmountExpected:    0,
			err:                   true,
		},
		{
			desc: "evaluate bump amount in right open interval",
			partialLinearFunction: &PartialLinearFunction{
				LeftClosedEndpoint: 0,
				RightOpenEndpoint:  100,
				Slope:              2,
				Intercept:          100,
			},
			feeRateInSatsPerVByte: 100,
			bumpAmountExpected:    0,
			err:                   true,
		},
	}

	for _, tC := range testCases {
		t.Run(tC.desc, func(t *testing.T) {
			bumpAmount, err := tC.partialLinearFunction.evaluate(tC.feeRateInSatsPerVByte)

			if err == nil && tC.err {
				t.Fatal("expected error")
			}

			if bumpAmount != tC.bumpAmountExpected {
				t.Fatalf("expected fee bump to be %v, but got %v", bumpAmount, tC.bumpAmountExpected)
			}
		})
	}
}

func TestFeeBumpFunction(t *testing.T) {
	testCases := []struct {
		desc                  string
		feeBumpFunction       *FeeBumpFunction
		feeRateInSatsPerVByte float64
		bumpAmountExpected    int64
		err                   bool
	}{
		{
			desc: "fee bump function with one partial function",
			feeBumpFunction: &FeeBumpFunction{
				PartialLinearFunctions: []*PartialLinearFunction{
					{
						LeftClosedEndpoint: 0,
						RightOpenEndpoint:  math.Inf(1),
						Slope:              2,
						Intercept:          100,
					},
				},
			},
			feeRateInSatsPerVByte: 100,
			bumpAmountExpected:    300,
			err:                   false,
		},
		{
			desc: "fee bump function with two partial functions",
			feeBumpFunction: &FeeBumpFunction{
				PartialLinearFunctions: []*PartialLinearFunction{
					{
						LeftClosedEndpoint: 0,
						RightOpenEndpoint:  100,
						Slope:              2,
						Intercept:          100,
					},
					{
						LeftClosedEndpoint: 100,
						RightOpenEndpoint:  math.Inf(1),
						Slope:              3,
						Intercept:          200,
					},
				},
			},
			feeRateInSatsPerVByte: 200,
			bumpAmountExpected:    800,
			err:                   false,
		},
		{
			desc: "fee bump function without partial functions should return error",
			feeBumpFunction: &FeeBumpFunction{
				PartialLinearFunctions: nil,
			},
			feeRateInSatsPerVByte: 100,
			bumpAmountExpected:    0,
			err:                   true,
		},
		{
			desc: "fee bump function with fee rate outside any interval should return error",
			feeBumpFunction: &FeeBumpFunction{
				PartialLinearFunctions: []*PartialLinearFunction{
					{
						LeftClosedEndpoint: 0,
						RightOpenEndpoint:  math.Inf(1),
						Slope:              2,
						Intercept:          100,
					},
				},
			},
			feeRateInSatsPerVByte: -1,
			bumpAmountExpected:    0,
			err:                   true,
		},
	}

	for _, tC := range testCases {
		t.Run(tC.desc, func(t *testing.T) {
			bumpAmount, err := tC.feeBumpFunction.GetBumpAmountForFeeRate(tC.feeRateInSatsPerVByte)

			if err == nil && tC.err {
				t.Fatal("expected error")
			}

			if bumpAmount != tC.bumpAmountExpected {
				t.Fatalf("expected fee bump to be %v, but got %v", bumpAmount, tC.bumpAmountExpected)
			}
		})
	}
}
