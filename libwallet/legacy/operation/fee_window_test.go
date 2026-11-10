package operation

import "testing"

var singleFeeWindow = &FeeWindow{
	TargetedFees: func() map[uint]float64 {
		fees := make(map[uint]float64)
		fees[1] = 5.6
		return fees
	}(),
}

var someFeeWindow = &FeeWindow{
	TargetedFees: func() map[uint]float64 {
		fees := make(map[uint]float64)
		fees[2] = 2.3
		fees[5] = 7.2
		fees[20] = 18.7
		return fees
	}(),
}

func TestFastestFeeRate(t *testing.T) {

	testCases := []struct {
		desc            string
		feewindow       *FeeWindow
		expectedFeeRate float64
	}{
		{
			desc:            "returns the fastest fee rate",
			feewindow:       someFeeWindow,
			expectedFeeRate: 2.3,
		},
		{
			desc:            "returns the only fee rate as fastest",
			feewindow:       singleFeeWindow,
			expectedFeeRate: 5.6,
		},
	}

	for _, tC := range testCases {
		t.Run(tC.desc, func(t *testing.T) {

			feeRate := tC.feewindow.FastestFeeRate()

			if feeRate != tC.expectedFeeRate {
				t.Fatalf("expected feeRate = %v, got %v", tC.expectedFeeRate, feeRate)
			}
		})
	}
}

func TestMinimumFeeRate(t *testing.T) {
	testCases := []struct {
		desc            string
		feewindow       *FeeWindow
		confTarget      uint
		expectedFeeRate float64
	}{
		{
			desc:            "returns the exact target as closest, if present (1)",
			feewindow:       someFeeWindow,
			confTarget:      2,
			expectedFeeRate: 2.3,
		},
		{
			desc:            "returns the exact target as closest, if present (2)",
			feewindow:       someFeeWindow,
			confTarget:      5,
			expectedFeeRate: 7.2,
		},
		{
			desc:            "returns the exact target as closest, if present (3)",
			feewindow:       someFeeWindow,
			confTarget:      20,
			expectedFeeRate: 18.7,
		},
		{
			desc:            "returns the closest lower target (1)",
			feewindow:       someFeeWindow,
			confTarget:      4,
			expectedFeeRate: 2.3,
		},
		{
			desc:            "returns the closest lower target (2)",
			feewindow:       someFeeWindow,
			confTarget:      15,
			expectedFeeRate: 7.2,
		},
		{
			desc:            "returns the closest lower target (3)",
			feewindow:       someFeeWindow,
			confTarget:      22,
			expectedFeeRate: 18.7,
		},
		{
			desc:            "returns the lowest target by default",
			feewindow:       someFeeWindow,
			confTarget:      1,
			expectedFeeRate: 2.3,
		},
		{
			desc:            "returns the only fee rate as closest (1)",
			feewindow:       singleFeeWindow,
			confTarget:      1,
			expectedFeeRate: 5.6,
		},
		{
			desc:            "returns the only fee rate as closest (2)",
			feewindow:       singleFeeWindow,
			confTarget:      6,
			expectedFeeRate: 5.6,
		}, {
			desc:            "returns the only fee rate as closest (3)",
			feewindow:       singleFeeWindow,
			confTarget:      18,
			expectedFeeRate: 5.6,
		}, {
			desc:            "returns the only fee rate as closest (4)",
			feewindow:       singleFeeWindow,
			confTarget:      24,
			expectedFeeRate: 5.6,
		},
	}

	for _, tC := range testCases {
		t.Run(tC.desc, func(t *testing.T) {

			feeRate, err := tC.feewindow.MinimumFeeRate(tC.confTarget)

			if err != nil {
				t.Fatal(err)
			}
			if feeRate != tC.expectedFeeRate {
				t.Fatalf("expected feeRate = %v, got %v", tC.expectedFeeRate, feeRate)
			}
		})
	}
}

func TestInvalidConfirmationTargets(t *testing.T) {
	testCases := []struct {
		desc       string
		feewindow  *FeeWindow
		confTarget uint
	}{
		{
			desc:       "fails check when confirmation target is 0",
			feewindow:  someFeeWindow,
			confTarget: 0,
		},
	}

	for _, tC := range testCases {
		t.Run(tC.desc, func(t *testing.T) {

			_, err := tC.feewindow.MinimumFeeRate(tC.confTarget)

			if err == nil {
				t.Fatalf("expected test to error")
			}
		})
	}
}

func TestNextHighestBlock(t *testing.T) {
	block := someFeeWindow.NextHighestBlock(10.0)
	if block != 2 {
		t.Fatalf("expected block to be 2, got %v", block)
	}
}
