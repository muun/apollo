package operation

import (
	"testing"
)

var emptyNts = &NextTransactionSize{}

var defaultNts = &NextTransactionSize{
	SizeProgression: []SizeForAmount{
		{
			AmountInSat: 103_456,
			SizeInVByte: 110,
		},
		{
			AmountInSat: 20_345_678,
			SizeInVByte: 230,
		},
		{
			AmountInSat: 303_456_789,
			SizeInVByte: 340,
		},
		{
			AmountInSat: 703_456_789,
			SizeInVByte: 580,
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
				calculator := feeCalculator{&nts}
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
			}},
			feeRateInSatsPerVbyte: 10,
			takeFeeFromAmount:     false,
			expectedFeeInSat:      2400,
		},
		{
			desc:                  "fails when balance is zero",
			amountInSat:           1,
			feeCalculator:         &feeCalculator{emptyNts},
			feeRateInSatsPerVbyte: 10,
			takeFeeFromAmount:     false,
			expectedFeeInSat:      0,
		},
		{
			desc:                  "fails when balance is zero with TFFA",
			amountInSat:           1,
			feeCalculator:         &feeCalculator{emptyNts},
			feeRateInSatsPerVbyte: 10,
			takeFeeFromAmount:     true,
			expectedFeeInSat:      0,
		},
		{
			desc:                  "fails when amount greater than balance",
			amountInSat:           defaultNts.TotalBalance() + 1,
			feeCalculator:         &feeCalculator{defaultNts},
			feeRateInSatsPerVbyte: 10,
			takeFeeFromAmount:     false,
			expectedFeeInSat:      5800,
		},
		{
			desc:                  "fails when amount greater than balance with TFFA",
			amountInSat:           defaultNts.TotalBalance() + 1,
			feeCalculator:         &feeCalculator{defaultNts},
			feeRateInSatsPerVbyte: 10,
			takeFeeFromAmount:     true,
			expectedFeeInSat:      5800,
		},
		{
			desc:                  "calculates when amount plus fee is greater than balance",
			amountInSat:           defaultNts.TotalBalance() - 1,
			feeCalculator:         &feeCalculator{defaultNts},
			feeRateInSatsPerVbyte: 10,
			takeFeeFromAmount:     false,
			expectedFeeInSat:      5800,
		},
		{
			desc:                  "calculates reduced amount and fee with TFFA",
			amountInSat:           10_345_678,
			feeCalculator:         &feeCalculator{defaultNts},
			feeRateInSatsPerVbyte: 10,
			takeFeeFromAmount:     true,
			expectedFeeInSat:      2300,
		},
		{
			// This case can't really happen since our PaymentAnalyzer enforces amount ==  totalBalance for TFFA
			// We don't handle that precondition in FeeCalculator to keep its API simple (no error handling)
			desc:                  "calculates when no amount is left after TFFA",
			amountInSat:           10,
			feeCalculator:         &feeCalculator{defaultNts},
			feeRateInSatsPerVbyte: 10,
			takeFeeFromAmount:     true,
			expectedFeeInSat:      1100,
		},
		{
			desc:                  "calculates use-all-funds fee with TFFA",
			amountInSat:           defaultNTS.TotalBalance(),
			feeCalculator:         &feeCalculator{defaultNts},
			feeRateInSatsPerVbyte: 10,
			takeFeeFromAmount:     true,
			expectedFeeInSat:      2300,
		},
		{
			desc:                  "calculates when paying fee does not require an additional UTXO (1)",
			amountInSat:           defaultNts.SizeProgression[0].AmountInSat / 2,
			feeCalculator:         &feeCalculator{defaultNts},
			feeRateInSatsPerVbyte: 10,
			takeFeeFromAmount:     false,
			expectedFeeInSat:      defaultNts.SizeProgression[0].SizeInVByte * 10,
		},
		{
			desc:                  "calculates when paying fee does not require an additional UTXO (2)",
			amountInSat:           defaultNts.SizeProgression[1].AmountInSat / 2,
			feeCalculator:         &feeCalculator{defaultNts},
			feeRateInSatsPerVbyte: 10,
			takeFeeFromAmount:     false,
			expectedFeeInSat:      defaultNts.SizeProgression[1].SizeInVByte * 10,
		},
		{
			desc:                  "calculates when paying fee does not require an additional UTXO (3)",
			amountInSat:           defaultNts.SizeProgression[2].AmountInSat / 2,
			feeCalculator:         &feeCalculator{defaultNts},
			feeRateInSatsPerVbyte: 10,
			takeFeeFromAmount:     false,
			expectedFeeInSat:      defaultNts.SizeProgression[2].SizeInVByte * 10,
		},
		{
			desc:                  "calculates when paying fee does not require an additional UTXO (4)",
			amountInSat:           defaultNts.SizeProgression[3].AmountInSat / 2,
			feeCalculator:         &feeCalculator{defaultNts},
			feeRateInSatsPerVbyte: 10,
			takeFeeFromAmount:     false,
			expectedFeeInSat:      defaultNts.SizeProgression[3].SizeInVByte * 10,
		},
		{
			desc:                  "calculates when paying fee requires an additional UTXO (1)",
			amountInSat:           defaultNts.SizeProgression[0].AmountInSat - 1,
			feeCalculator:         &feeCalculator{defaultNts},
			feeRateInSatsPerVbyte: 10,
			takeFeeFromAmount:     false,
			expectedFeeInSat:      defaultNts.SizeProgression[1].SizeInVByte * 10,
		},
		{
			desc:                  "calculates when paying fee requires an additional UTXO (2)",
			amountInSat:           defaultNts.SizeProgression[1].AmountInSat - 1,
			feeCalculator:         &feeCalculator{defaultNts},
			feeRateInSatsPerVbyte: 10,
			takeFeeFromAmount:     false,
			expectedFeeInSat:      defaultNts.SizeProgression[2].SizeInVByte * 10,
		},
		{
			desc:                  "calculates when paying fee requires an additional UTXO (3)",
			amountInSat:           defaultNts.SizeProgression[2].AmountInSat - 1,
			feeCalculator:         &feeCalculator{defaultNts},
			feeRateInSatsPerVbyte: 10,
			takeFeeFromAmount:     false,
			expectedFeeInSat:      defaultNts.SizeProgression[3].SizeInVByte * 10,
		},
		{
			desc:                  "calculates when negative UTXOs are larger than positive UTXOs",
			amountInSat:           1,
			feeCalculator:         &feeCalculator{singleNegativeUtxoNts},
			feeRateInSatsPerVbyte: 10,
			takeFeeFromAmount:     false,
			expectedFeeInSat:      8400, // which is > 64, aka singleNegativeUtxoNts.TotalBalance()
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
