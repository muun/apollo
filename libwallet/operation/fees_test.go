package operation

import (
	"math"
	"testing"
)

var emptyNts = &NextTransactionSize{}

var defaultNts = &NextTransactionSize{
	SizeProgression: []SizeForAmount{
		{
			AmountInSat: 103_456,
			SizeInVByte: 110,
			Outpoint:    "0437cd7f8525ceed2324359c2d0ba26006d92d856a9c20fa0241106ee5a597c1:0",
			UtxoStatus:  UtxosStatusUnconfirmed,
		},
		{
			AmountInSat: 20_345_678,
			SizeInVByte: 230,
			Outpoint:    "0437cd7f8525ceed2324359c2d0ba26006d92d856a9c20fa0241106ee5a597c2:0",
			UtxoStatus:  UtxosStatusConfirmed,
		},
		{
			AmountInSat: 303_456_789,
			SizeInVByte: 340,
			Outpoint:    "0437cd7f8525ceed2324359c2d0ba26006d92d856a9c20fa0241106ee5a597c3:0",
			UtxoStatus:  UtxosStatusUnconfirmed,
		},
		{
			AmountInSat: 703_456_789,
			SizeInVByte: 580,
			Outpoint:    "0437cd7f8525ceed2324359c2d0ba26006d92d856a9c20fa0241106ee5a597c4:0",
			UtxoStatus:  UtxosStatusConfirmed,
		},
	},
	ExpectedDebtInSat: 0,
}

var singleNts = &NextTransactionSize{
	SizeProgression: []SizeForAmount{
		{
			AmountInSat: 123_456,
			SizeInVByte: 400,
		},
	},
	ExpectedDebtInSat: 0,
}

// 2nd utxo is actually more expensive to spend that what its worth
var negativeUtxoNts = &NextTransactionSize{
	SizeProgression: []SizeForAmount{
		{
			AmountInSat: 48_216,
			SizeInVByte: 840,
		},
		{
			AmountInSat: 48_880,
			SizeInVByte: 1366,
		},
	},
	ExpectedDebtInSat: 0,
}

// Utxo is actually more expensive to spend that what its worth
var singleNegativeUtxoNts = &NextTransactionSize{
	SizeProgression: []SizeForAmount{
		{
			AmountInSat: 644,
			SizeInVByte: 840,
		},
	},
	ExpectedDebtInSat: 0,
}

var firstFeeBumpFunction = []*PartialLinearFunction{
	&PartialLinearFunction{
		LeftClosedEndpoint: 0,
		RightOpenEndpoint:  50,
		Slope:              2,
		Intercept:          100,
	},
	&PartialLinearFunction{
		LeftClosedEndpoint: 50,
		RightOpenEndpoint:  100,
		Slope:              3,
		Intercept:          200,
	},
	&PartialLinearFunction{
		LeftClosedEndpoint: 100,
		RightOpenEndpoint:  math.Inf(1),
		Slope:              4,
		Intercept:          300,
	},
}

var secondFeeBumpFunction = []*PartialLinearFunction{
	&PartialLinearFunction{
		LeftClosedEndpoint: 100,
		RightOpenEndpoint:  math.Inf(1),
		Slope:              7,
		Intercept:          300,
	},
	&PartialLinearFunction{
		LeftClosedEndpoint: 50,
		RightOpenEndpoint:  100,
		Slope:              6,
		Intercept:          200,
	},
	&PartialLinearFunction{
		LeftClosedEndpoint: 0,
		RightOpenEndpoint:  50,
		Slope:              5,
		Intercept:          100,
	},
}

func TestFeeCalculatorForAmountZero(t *testing.T) {
	testCases := []struct {
		desc                  string
		feeRateInSatsPerVbyte float64
		takeFeeFromAmount     bool
		expectedFeeInSat      int64
	}{
		{
			desc:                  "calculate for amount zero",
			feeRateInSatsPerVbyte: 1,
			takeFeeFromAmount:     false,
			expectedFeeInSat:      0,
		},
		{
			desc:                  "calculate for amount zero with TFFA",
			feeRateInSatsPerVbyte: 1,
			takeFeeFromAmount:     true,
			expectedFeeInSat:      0,
		},
	}

	for _, tC := range testCases {
		t.Run(tC.desc, func(t *testing.T) {

			allNts := []NextTransactionSize{
				*emptyNts,
				*defaultNts,
				*singleNts,
				*negativeUtxoNts,
				*singleNegativeUtxoNts,
			}

			for _, nts := range allNts {
				calculator := feeCalculator{&nts, nil}
				feeInSat := calculator.Fee(0, tC.feeRateInSatsPerVbyte, tC.takeFeeFromAmount)

				if feeInSat != tC.expectedFeeInSat {
					t.Fatalf("expected fee = %v, got %v", tC.expectedFeeInSat, feeInSat)
				}
			}

			calculator := feeCalculator{}
			feeInSat := calculator.Fee(0, tC.feeRateInSatsPerVbyte, tC.takeFeeFromAmount)

			if feeInSat != tC.expectedFeeInSat {
				t.Fatalf("expected fee = %v, got %v", tC.expectedFeeInSat, feeInSat)
			}
		})
	}
}

func TestFeeCalculator(t *testing.T) {

	testCases := []struct {
		desc                  string
		amountInSat           int64
		feeCalculator         *feeCalculator
		feeRateInSatsPerVbyte float64
		takeFeeFromAmount     bool
		expectedFeeInSat      int64
	}{
		{
			desc:                  "empty fee calculator",
			amountInSat:           1000,
			feeCalculator:         &feeCalculator{},
			feeRateInSatsPerVbyte: 10,
			takeFeeFromAmount:     false,
			expectedFeeInSat:      0,
		},
		{
			desc:                  "empty fee calculator with TFFA",
			amountInSat:           1000,
			feeCalculator:         &feeCalculator{},
			feeRateInSatsPerVbyte: 10,
			takeFeeFromAmount:     false,
			expectedFeeInSat:      0,
		},
		{
			desc:        "non empty fee calculator",
			amountInSat: 1000,
			feeCalculator: &feeCalculator{&NextTransactionSize{
				SizeProgression: []SizeForAmount{
					{
						AmountInSat: 10_000,
						SizeInVByte: 240,
					},
				},
				ExpectedDebtInSat: 0,
			},
				nil,
			},
			feeRateInSatsPerVbyte: 10,
			takeFeeFromAmount:     false,
			expectedFeeInSat:      2400,
		},
		{
			desc:                  "fails when balance is zero",
			amountInSat:           1,
			feeCalculator:         &feeCalculator{emptyNts, nil},
			feeRateInSatsPerVbyte: 10,
			takeFeeFromAmount:     false,
			expectedFeeInSat:      0,
		},
		{
			desc:                  "fails when balance is zero with TFFA",
			amountInSat:           1,
			feeCalculator:         &feeCalculator{emptyNts, nil},
			feeRateInSatsPerVbyte: 10,
			takeFeeFromAmount:     true,
			expectedFeeInSat:      0,
		},
		{
			desc:                  "fails when amount greater than balance",
			amountInSat:           defaultNts.TotalBalance() + 1,
			feeCalculator:         &feeCalculator{defaultNts, nil},
			feeRateInSatsPerVbyte: 10,
			takeFeeFromAmount:     false,
			expectedFeeInSat:      5800,
		},
		{
			desc:                  "fails when amount greater than balance with TFFA",
			amountInSat:           defaultNts.TotalBalance() + 1,
			feeCalculator:         &feeCalculator{defaultNts, nil},
			feeRateInSatsPerVbyte: 10,
			takeFeeFromAmount:     true,
			expectedFeeInSat:      5800,
		},
		{
			desc:                  "calculates when amount plus fee is greater than balance",
			amountInSat:           defaultNts.TotalBalance() - 1,
			feeCalculator:         &feeCalculator{defaultNts, nil},
			feeRateInSatsPerVbyte: 10,
			takeFeeFromAmount:     false,
			expectedFeeInSat:      5800,
		},
		{
			desc:                  "calculates reduced amount and fee with TFFA",
			amountInSat:           10_345_678,
			feeCalculator:         &feeCalculator{defaultNts, nil},
			feeRateInSatsPerVbyte: 10,
			takeFeeFromAmount:     true,
			expectedFeeInSat:      2300,
		},
		{
			// This case can't really happen since our PaymentAnalyzer enforces amount ==  totalBalance for TFFA
			// We don't handle that precondition in FeeCalculator to keep its API simple (no error handling)
			desc:                  "calculates when no amount is left after TFFA",
			amountInSat:           10,
			feeCalculator:         &feeCalculator{defaultNts, nil},
			feeRateInSatsPerVbyte: 10,
			takeFeeFromAmount:     true,
			expectedFeeInSat:      1100,
		},
		{
			desc:                  "calculates use-all-funds fee with TFFA",
			amountInSat:           defaultNTS.TotalBalance(),
			feeCalculator:         &feeCalculator{defaultNts, nil},
			feeRateInSatsPerVbyte: 10,
			takeFeeFromAmount:     true,
			expectedFeeInSat:      2300,
		},
		{
			desc:                  "calculates when paying fee does not require an additional UTXO (1)",
			amountInSat:           defaultNts.SizeProgression[0].AmountInSat / 2,
			feeCalculator:         &feeCalculator{defaultNts, nil},
			feeRateInSatsPerVbyte: 10,
			takeFeeFromAmount:     false,
			expectedFeeInSat:      defaultNts.SizeProgression[0].SizeInVByte * 10,
		},
		{
			desc:                  "calculates when paying fee does not require an additional UTXO (2)",
			amountInSat:           defaultNts.SizeProgression[1].AmountInSat / 2,
			feeCalculator:         &feeCalculator{defaultNts, nil},
			feeRateInSatsPerVbyte: 10,
			takeFeeFromAmount:     false,
			expectedFeeInSat:      defaultNts.SizeProgression[1].SizeInVByte * 10,
		},
		{
			desc:                  "calculates when paying fee does not require an additional UTXO (3)",
			amountInSat:           defaultNts.SizeProgression[2].AmountInSat / 2,
			feeCalculator:         &feeCalculator{defaultNts, nil},
			feeRateInSatsPerVbyte: 10,
			takeFeeFromAmount:     false,
			expectedFeeInSat:      defaultNts.SizeProgression[2].SizeInVByte * 10,
		},
		{
			desc:                  "calculates when paying fee does not require an additional UTXO (4)",
			amountInSat:           defaultNts.SizeProgression[3].AmountInSat / 2,
			feeCalculator:         &feeCalculator{defaultNts, nil},
			feeRateInSatsPerVbyte: 10,
			takeFeeFromAmount:     false,
			expectedFeeInSat:      defaultNts.SizeProgression[3].SizeInVByte * 10,
		},
		{
			desc:                  "calculates when paying fee requires an additional UTXO (1)",
			amountInSat:           defaultNts.SizeProgression[0].AmountInSat - 1,
			feeCalculator:         &feeCalculator{defaultNts, nil},
			feeRateInSatsPerVbyte: 10,
			takeFeeFromAmount:     false,
			expectedFeeInSat:      defaultNts.SizeProgression[1].SizeInVByte * 10,
		},
		{
			desc:                  "calculates when paying fee requires an additional UTXO (2)",
			amountInSat:           defaultNts.SizeProgression[1].AmountInSat - 1,
			feeCalculator:         &feeCalculator{defaultNts, nil},
			feeRateInSatsPerVbyte: 10,
			takeFeeFromAmount:     false,
			expectedFeeInSat:      defaultNts.SizeProgression[2].SizeInVByte * 10,
		},
		{
			desc:                  "calculates when paying fee requires an additional UTXO (3)",
			amountInSat:           defaultNts.SizeProgression[2].AmountInSat - 1,
			feeCalculator:         &feeCalculator{defaultNts, nil},
			feeRateInSatsPerVbyte: 10,
			takeFeeFromAmount:     false,
			expectedFeeInSat:      defaultNts.SizeProgression[3].SizeInVByte * 10,
		},
		{
			desc:                  "calculates when negative UTXOs are larger than positive UTXOs",
			amountInSat:           1,
			feeCalculator:         &feeCalculator{singleNegativeUtxoNts, nil},
			feeRateInSatsPerVbyte: 10,
			takeFeeFromAmount:     false,
			expectedFeeInSat:      8400, // which is > 64, aka singleNegativeUtxoNts.TotalBalance()
		},
		{
			desc:        "calculates when feeBumpFunctions is loaded using 1 utxo unconfirmed",
			amountInSat: defaultNts.SizeProgression[1].AmountInSat / 2,
			feeCalculator: &feeCalculator{
				NextTransactionSize: defaultNts,
				feeBumpFunctions: []*FeeBumpFunction{
					&FeeBumpFunction{PartialLinearFunctions: firstFeeBumpFunction},
					&FeeBumpFunction{PartialLinearFunctions: secondFeeBumpFunction},
				},
			},
			feeRateInSatsPerVbyte: 10,
			takeFeeFromAmount:     false,
			expectedFeeInSat:      2420,
		},
		{
			desc:        "calculates when feeBumpFunctions is loaded using 2 unconfirmed utxos",
			amountInSat: defaultNts.SizeProgression[2].AmountInSat / 2,
			feeCalculator: &feeCalculator{
				NextTransactionSize: defaultNts,
				feeBumpFunctions: []*FeeBumpFunction{
					&FeeBumpFunction{PartialLinearFunctions: firstFeeBumpFunction},
					&FeeBumpFunction{PartialLinearFunctions: secondFeeBumpFunction},
				},
			},
			feeRateInSatsPerVbyte: 10,
			takeFeeFromAmount:     false,
			expectedFeeInSat:      3550,
		},
		{
			desc:        "calculates when we have less feeBumpFunctions than unconfirmed utxos (use the last function)",
			amountInSat: defaultNts.SizeProgression[2].AmountInSat,
			feeCalculator: &feeCalculator{
				NextTransactionSize: defaultNts,
				feeBumpFunctions: []*FeeBumpFunction{
					&FeeBumpFunction{PartialLinearFunctions: firstFeeBumpFunction},
				},
			},
			feeRateInSatsPerVbyte: 10,
			takeFeeFromAmount:     true,
			expectedFeeInSat:      3520,
		},
		{
			desc:        "calculates when it does not have unconfirmed utxos",
			amountInSat: singleNts.SizeProgression[0].AmountInSat / 2,
			feeCalculator: &feeCalculator{
				NextTransactionSize: singleNts,
				feeBumpFunctions: []*FeeBumpFunction{
					&FeeBumpFunction{PartialLinearFunctions: firstFeeBumpFunction},
					&FeeBumpFunction{PartialLinearFunctions: secondFeeBumpFunction},
				},
			},
			feeRateInSatsPerVbyte: 10,
			takeFeeFromAmount:     false,
			expectedFeeInSat:      4000,
		},
		{
			desc:        "calculates when feeRate is exactly on left/right endpoint",
			amountInSat: defaultNts.SizeProgression[0].AmountInSat / 2,
			feeCalculator: &feeCalculator{
				NextTransactionSize: defaultNts,
				feeBumpFunctions: []*FeeBumpFunction{
					&FeeBumpFunction{PartialLinearFunctions: firstFeeBumpFunction},
					&FeeBumpFunction{PartialLinearFunctions: secondFeeBumpFunction},
				},
			},
			feeRateInSatsPerVbyte: 100,
			takeFeeFromAmount:     false,
			expectedFeeInSat:      11700,
		},
	}
	for _, tC := range testCases {
		t.Run(tC.desc, func(t *testing.T) {

			feeInSat := tC.feeCalculator.Fee(tC.amountInSat, tC.feeRateInSatsPerVbyte, tC.takeFeeFromAmount)

			if feeInSat != tC.expectedFeeInSat {
				t.Fatalf("expected fee = %v, got %v", tC.expectedFeeInSat, feeInSat)
			}
		})
	}
}
