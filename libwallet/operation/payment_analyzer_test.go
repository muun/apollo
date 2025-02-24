package operation

import (
	"math"
	"reflect"
	"testing"

	"github.com/muun/libwallet/fees"
)

var defaultFeeWindow = &FeeWindow{
	TargetedFees: func() map[uint]float64 {
		fees := make(map[uint]float64)
		fees[1] = 10.0
		fees[2] = 5.0
		fees[3] = 1.0
		return fees
	}(),
}

var feeWindowFailureTests = &FeeWindow{
	TargetedFees: func() map[uint]float64 {
		fees := make(map[uint]float64)
		fees[1] = 100.0
		fees[5] = 50.0
		fees[10] = .25
		return fees
	}(),
}

var defaultNTS = &NextTransactionSize{
	SizeProgression: []SizeForAmount{
		{
			AmountInSat: 1_000_000,
			SizeInVByte: 100,
		},
	},
	ExpectedDebtInSat: 0,
}

var partialLinearFunction = &PartialLinearFunction{
	LeftClosedEndpoint: 0,
	RightOpenEndpoint:  math.Inf(1),
	Slope:              2,
	Intercept:          100,
}

func TestAnalyzeOnChain(t *testing.T) {
	testCases := []struct {
		desc     string
		nts      *NextTransactionSize
		feeBump  []*FeeBumpFunction
		payment  *PaymentToAddress
		expected *PaymentAnalysis
		err      bool
	}{
		{
			desc: "success",
			payment: &PaymentToAddress{
				TakeFeeFromAmount:     false,
				AmountInSat:           10000,
				FeeRateInSatsPerVByte: 1,
			},
			expected: &PaymentAnalysis{
				Status:        AnalysisStatusOk,
				AmountInSat:   10000,
				FeeTotalInSat: 100,
				FeeBumpInSat:  0,
				TotalInSat:    10100,
			},
		},
		{
			desc: "take fee from amount",
			payment: &PaymentToAddress{
				AmountInSat:           1_000_000,
				TakeFeeFromAmount:     true,
				FeeRateInSatsPerVByte: 1,
			},
			expected: &PaymentAnalysis{
				Status:        AnalysisStatusOk,
				AmountInSat:   999_900,
				FeeTotalInSat: 100,
				FeeBumpInSat:  0,
				TotalInSat:    1_000_000,
			},
		},
		{
			desc: "zero amount",
			payment: &PaymentToAddress{
				TakeFeeFromAmount:     false,
				AmountInSat:           0,
				FeeRateInSatsPerVByte: 1,
			},
			expected: &PaymentAnalysis{
				Status: AnalysisStatusAmountTooSmall,
			},
		},
		{
			desc: "zero amount using TFFA",
			payment: &PaymentToAddress{
				TakeFeeFromAmount:     true,
				AmountInSat:           0,
				FeeRateInSatsPerVByte: 1,
			},
			expected: &PaymentAnalysis{
				Status: AnalysisStatusAmountTooSmall,
			},
		},
		{
			desc: "sub dust amount",
			payment: &PaymentToAddress{
				TakeFeeFromAmount:     false,
				AmountInSat:           100,
				FeeRateInSatsPerVByte: 1,
			},
			expected: &PaymentAnalysis{
				Status: AnalysisStatusAmountTooSmall,
			},
		},
		{
			desc: "sub dust amount using TFFA",
			payment: &PaymentToAddress{
				TakeFeeFromAmount:     true,
				AmountInSat:           100,
				FeeRateInSatsPerVByte: 1,
			},
			expected: &PaymentAnalysis{
				Status: AnalysisStatusAmountTooSmall,
			},
		},
		{
			desc: "amount greater than balance",
			payment: &PaymentToAddress{
				TakeFeeFromAmount:     false,
				AmountInSat:           1_000_000_000,
				FeeRateInSatsPerVByte: 1,
			},
			expected: &PaymentAnalysis{
				Status:     AnalysisStatusAmountGreaterThanBalance,
				TotalInSat: 1_000_000_000,
			},
		},
		{
			desc: "valid amount plus selected fee",
			nts: &NextTransactionSize{
				SizeProgression: []SizeForAmount{
					{
						AmountInSat: 10_000,
						SizeInVByte: 240,
					},
				},
				ExpectedDebtInSat: 0,
			},
			payment: &PaymentToAddress{
				TakeFeeFromAmount:     false,
				AmountInSat:           5000,
				FeeRateInSatsPerVByte: 10,
			},
			expected: &PaymentAnalysis{
				Status:        AnalysisStatusOk,
				AmountInSat:   5000,
				FeeTotalInSat: 2400,
				FeeBumpInSat:  0,
				TotalInSat:    7400,
			},
		},
		{
			desc: "valid amount but unpayable because of fee",
			nts: &NextTransactionSize{
				SizeProgression: []SizeForAmount{
					{
						AmountInSat: 10_000,
						SizeInVByte: 240,
					},
				},
				ExpectedDebtInSat: 0,
			},
			payment: &PaymentToAddress{
				TakeFeeFromAmount:     false,
				AmountInSat:           9000,
				FeeRateInSatsPerVByte: 10,
			},
			expected: &PaymentAnalysis{
				Status:        AnalysisStatusUnpayable,
				AmountInSat:   9000,
				FeeTotalInSat: 2400,
				FeeBumpInSat:  0,
				TotalInSat:    11400,
			},
		},
		{
			desc: "valid amount but unpayable with any fee",
			nts: &NextTransactionSize{
				SizeProgression: []SizeForAmount{
					{
						AmountInSat: 10_000,
						SizeInVByte: 240,
					},
				},
				ExpectedDebtInSat: 0,
			},
			payment: &PaymentToAddress{
				TakeFeeFromAmount:     false,
				AmountInSat:           9999,
				FeeRateInSatsPerVByte: 10,
			},
			expected: &PaymentAnalysis{
				Status:        AnalysisStatusUnpayable,
				AmountInSat:   9999,
				FeeTotalInSat: 2400,
				FeeBumpInSat:  0,
				TotalInSat:    12399,
			},
		},
		{
			desc: "success with fee bump",
			nts: &NextTransactionSize{
				SizeProgression: []SizeForAmount{
					{
						AmountInSat: 10_000,
						SizeInVByte: 240,
						Outpoint:    "0437cd7f8525ceed2324359c2d0ba26006d92d856a9c20fa0241106ee5a597c4:0",
						UtxoStatus:  UtxosStatusUnconfirmed,
					},
				},
				ExpectedDebtInSat: 0,
			},
			feeBump: []*FeeBumpFunction{
				&FeeBumpFunction{
					[]*PartialLinearFunction{
						partialLinearFunction,
					}},
				&FeeBumpFunction{
					[]*PartialLinearFunction{
						partialLinearFunction,
					}},
				&FeeBumpFunction{
					[]*PartialLinearFunction{
						partialLinearFunction,
					}},
			},
			payment: &PaymentToAddress{
				TakeFeeFromAmount:     false,
				AmountInSat:           7480,
				FeeRateInSatsPerVByte: 10,
			},
			expected: &PaymentAnalysis{
				Status:        AnalysisStatusOk,
				AmountInSat:   7480,
				FeeTotalInSat: 2520,
				FeeBumpInSat:  120,
				TotalInSat:    10000,
			},
		},
		{
			desc: "fee bump with several unconfirmed utxos",
			nts: &NextTransactionSize{
				SizeProgression: []SizeForAmount{
					{
						AmountInSat: 10_000,
						SizeInVByte: 240,
						Outpoint:    "0437cd7f8525ceed2324359c2d0ba26006d92d856a9c20fa0241106ee5a597c4:0",
						UtxoStatus:  UtxosStatusConfirmed,
					},
					{
						AmountInSat: 20_000,
						SizeInVByte: 440,
						Outpoint:    "0437cd7f8525ceed2324359c2d0ba26006d92d856a9c20fa0241106ee5a597c3:0",
						UtxoStatus:  UtxosStatusUnconfirmed,
					},
					{
						AmountInSat: 30_000,
						SizeInVByte: 780,
						Outpoint:    "0437cd7f8525ceed2324359c2d0ba26006d92d856a9c20fa0241106ee5a597c2:0",
						UtxoStatus:  UtxosStatusUnconfirmed,
					},
				},
				ExpectedDebtInSat: 0,
			},
			feeBump: []*FeeBumpFunction{
				&FeeBumpFunction{firstFeeBumpFunction},
				&FeeBumpFunction{secondFeeBumpFunction},
			},
			payment: &PaymentToAddress{
				TakeFeeFromAmount:     false,
				AmountInSat:           12000,
				FeeRateInSatsPerVByte: 10,
			},
			expected: &PaymentAnalysis{
				Status:        AnalysisStatusOk,
				AmountInSat:   12000,
				FeeTotalInSat: 4520,
				FeeBumpInSat:  120,
				TotalInSat:    16520,
			},
		},
		{
			desc: "fee bump with total balance and takeFeeFromAmount is true",
			nts: &NextTransactionSize{
				SizeProgression: []SizeForAmount{
					{
						AmountInSat: 10_000,
						SizeInVByte: 240,
						Outpoint:    "0437cd7f8525ceed2324359c2d0ba26006d92d856a9c20fa0241106ee5a597c4:0",
						UtxoStatus:  UtxosStatusConfirmed,
					},
					{
						AmountInSat: 20_000,
						SizeInVByte: 440,
						Outpoint:    "0437cd7f8525ceed2324359c2d0ba26006d92d856a9c20fa0241106ee5a597c3:0",
						UtxoStatus:  UtxosStatusUnconfirmed,
					},
					{
						AmountInSat: 30_000,
						SizeInVByte: 780,
						Outpoint:    "0437cd7f8525ceed2324359c2d0ba26006d92d856a9c20fa0241106ee5a597c2:0",
						UtxoStatus:  UtxosStatusUnconfirmed,
					},
				},
				ExpectedDebtInSat: 0,
			},
			feeBump: []*FeeBumpFunction{
				&FeeBumpFunction{firstFeeBumpFunction},
				&FeeBumpFunction{secondFeeBumpFunction},
			},
			payment: &PaymentToAddress{
				TakeFeeFromAmount:     true,
				AmountInSat:           30000,
				FeeRateInSatsPerVByte: 10,
			},
			expected: &PaymentAnalysis{
				Status:        AnalysisStatusOk,
				AmountInSat:   22050,
				FeeTotalInSat: 7950,
				FeeBumpInSat:  150,
				TotalInSat:    30000,
			},
		},
		{
			desc: "valid amount but unpayable because of fee bump",
			nts: &NextTransactionSize{
				SizeProgression: []SizeForAmount{
					{
						AmountInSat: 10_000,
						SizeInVByte: 240,
						Outpoint:    "0437cd7f8525ceed2324359c2d0ba26006d92d856a9c20fa0241106ee5a597c4:0",
						UtxoStatus:  UtxosStatusUnconfirmed,
					},
				},
				ExpectedDebtInSat: 0,
			},
			feeBump: []*FeeBumpFunction{
				&FeeBumpFunction{
					[]*PartialLinearFunction{
						partialLinearFunction,
					}},
			},
			payment: &PaymentToAddress{
				TakeFeeFromAmount:     false,
				AmountInSat:           7500,
				FeeRateInSatsPerVByte: 10,
			},
			expected: &PaymentAnalysis{
				Status:        AnalysisStatusUnpayable,
				AmountInSat:   7500,
				FeeTotalInSat: 2520,
				FeeBumpInSat:  120,
				TotalInSat:    10020,
			},
		},
		{
			desc: "valid amount plus fee using TFFA",
			nts: &NextTransactionSize{
				SizeProgression: []SizeForAmount{
					{
						AmountInSat: 10_000,
						SizeInVByte: 240,
					},
				},
				ExpectedDebtInSat: 0,
			},
			payment: &PaymentToAddress{
				TakeFeeFromAmount:     true,
				AmountInSat:           10_000,
				FeeRateInSatsPerVByte: 10,
			},
			expected: &PaymentAnalysis{
				Status:        AnalysisStatusOk,
				AmountInSat:   7600,
				FeeTotalInSat: 2400,
				FeeBumpInSat:  0,
				TotalInSat:    10_000,
			},
		},
		{
			desc: "invalid amount using TFFA",
			nts: &NextTransactionSize{
				SizeProgression: []SizeForAmount{
					{
						AmountInSat: 10_000,
						SizeInVByte: 240,
					},
				},
				ExpectedDebtInSat: 0,
			},
			payment: &PaymentToAddress{
				TakeFeeFromAmount:     true,
				AmountInSat:           9900,
				FeeRateInSatsPerVByte: 10,
			},
			err: true,
		},
		{
			desc: "valid amount but unpayable because of fee using TFFA",
			nts: &NextTransactionSize{
				SizeProgression: []SizeForAmount{
					{
						AmountInSat: 1_000,
						SizeInVByte: 240,
					},
				},
				ExpectedDebtInSat: 0,
			},
			payment: &PaymentToAddress{
				TakeFeeFromAmount:     true,
				AmountInSat:           1000,
				FeeRateInSatsPerVByte: 10,
			},
			expected: &PaymentAnalysis{
				Status:        AnalysisStatusUnpayable,
				AmountInSat:   0,
				FeeTotalInSat: 2400,
				FeeBumpInSat:  0,
				TotalInSat:    1000,
			},
		},
		{
			desc: "valid amount but unpayable with any fee using TFFA",
			nts: &NextTransactionSize{
				SizeProgression: []SizeForAmount{
					{
						AmountInSat: 600,
						SizeInVByte: 240,
					},
				},
				ExpectedDebtInSat: 0,
			},
			payment: &PaymentToAddress{
				TakeFeeFromAmount:     true,
				AmountInSat:           600,
				FeeRateInSatsPerVByte: 10,
			},
			expected: &PaymentAnalysis{
				Status:        AnalysisStatusUnpayable,
				AmountInSat:   0,
				FeeTotalInSat: 2400,
				FeeBumpInSat:  0,
				TotalInSat:    600,
			},
		},
		{
			desc: "success with debt > 0",
			nts: &NextTransactionSize{
				SizeProgression:   defaultNTS.SizeProgression,
				ExpectedDebtInSat: 10000,
			},
			payment: &PaymentToAddress{
				TakeFeeFromAmount:     false,
				AmountInSat:           10000,
				FeeRateInSatsPerVByte: 1,
			},
			expected: &PaymentAnalysis{
				Status:        AnalysisStatusOk,
				AmountInSat:   10000,
				FeeTotalInSat: 100,
				FeeBumpInSat:  0,
				TotalInSat:    10100,
			},
		},
		{
			desc: "take fee from amount success with debt > 0",
			nts: &NextTransactionSize{
				SizeProgression:   defaultNTS.SizeProgression,
				ExpectedDebtInSat: 10000,
			},
			payment: &PaymentToAddress{
				AmountInSat:           990_000,
				TakeFeeFromAmount:     true,
				FeeRateInSatsPerVByte: 1,
			},
			expected: &PaymentAnalysis{
				Status:        AnalysisStatusOk,
				AmountInSat:   989_900,
				FeeTotalInSat: 100,
				FeeBumpInSat:  0,
				TotalInSat:    990_000,
			},
		},
		{
			desc: "amount greater than balance because debt > 0",
			nts: &NextTransactionSize{
				SizeProgression:   defaultNTS.SizeProgression,
				ExpectedDebtInSat: 10000,
			},
			payment: &PaymentToAddress{
				TakeFeeFromAmount:     false,
				AmountInSat:           999_900,
				FeeRateInSatsPerVByte: 1,
			},
			expected: &PaymentAnalysis{
				Status:     AnalysisStatusAmountGreaterThanBalance,
				TotalInSat: 999_900,
			},
		},
		{
			desc: "unpayable because debt > 0",
			nts: &NextTransactionSize{
				SizeProgression:   defaultNTS.SizeProgression,
				ExpectedDebtInSat: 10000,
			},
			payment: &PaymentToAddress{
				TakeFeeFromAmount:     false,
				AmountInSat:           989_500,
				FeeRateInSatsPerVByte: 10,
			},
			expected: &PaymentAnalysis{
				Status:        AnalysisStatusUnpayable,
				AmountInSat:   989_500,
				FeeTotalInSat: 1000,
				FeeBumpInSat:  0,
				TotalInSat:    990_500,
			},
		},
		{
			desc: "amount greater than balance using TFFA because debt > 0",
			nts: &NextTransactionSize{
				SizeProgression:   defaultNTS.SizeProgression,
				ExpectedDebtInSat: 10000,
			},
			payment: &PaymentToAddress{
				TakeFeeFromAmount:     true,
				AmountInSat:           999_900,
				FeeRateInSatsPerVByte: 1,
			},
			expected: &PaymentAnalysis{
				Status:     AnalysisStatusAmountGreaterThanBalance,
				TotalInSat: 999_900,
			},
		},
		{
			desc: "unpayable using TFFA because debt > 0",
			nts: &NextTransactionSize{
				SizeProgression: []SizeForAmount{
					{
						AmountInSat: 10_000,
						SizeInVByte: 240,
					},
				},
				ExpectedDebtInSat: 8000,
			},
			payment: &PaymentToAddress{
				TakeFeeFromAmount:     true,
				AmountInSat:           2000,
				FeeRateInSatsPerVByte: 100,
			},
			expected: &PaymentAnalysis{
				Status:        AnalysisStatusUnpayable,
				AmountInSat:   0,
				FeeTotalInSat: 24000,
				FeeBumpInSat:  0,
				TotalInSat:    2000,
			},
		},
		{
			desc: "unpayable using TFFA because amount < DUST",
			nts: &NextTransactionSize{
				SizeProgression: []SizeForAmount{
					{
						AmountInSat: 10_000,
						SizeInVByte: 240,
					},
				},
				ExpectedDebtInSat: 7400,
			},
			payment: &PaymentToAddress{
				TakeFeeFromAmount:     true,
				AmountInSat:           2600,
				FeeRateInSatsPerVByte: 10,
			},
			expected: &PaymentAnalysis{
				Status:        AnalysisStatusUnpayable,
				AmountInSat:   200,
				FeeTotalInSat: 2400,
				FeeBumpInSat:  0,
				TotalInSat:    2600,
			},
		},
	}

	for _, tC := range testCases {
		t.Run(tC.desc, func(t *testing.T) {

			var analyzer *PaymentAnalyzer
			if tC.nts != nil {
				analyzer = NewPaymentAnalyzer(defaultFeeWindow, tC.nts, tC.feeBump)
			} else {
				analyzer = NewPaymentAnalyzer(defaultFeeWindow, defaultNTS, tC.feeBump)
			}

			analysis, err := analyzer.ToAddress(tC.payment)

			if err == nil && tC.err {
				t.Fatal("expected analysis to error")
			}
			if err != nil && !tC.err {
				t.Fatal(err)
			}
			if !reflect.DeepEqual(analysis, tC.expected) {
				t.Fatalf("analysis does not match expected, got %+v, expected %+v", analysis, tC.expected)
			}
		})
	}
}

func TestAnalyzeOnChainValidAmountButUnpayableWithAnyFee(t *testing.T) {
	analyzer := NewPaymentAnalyzer(defaultFeeWindow, &NextTransactionSize{
		SizeProgression: []SizeForAmount{
			{
				AmountInSat: 10_000,
				SizeInVByte: 240,
			},
		},
		ExpectedDebtInSat: 0,
	},
		nil,
	)

	analysis, err := analyzer.ToAddress(&PaymentToAddress{
		TakeFeeFromAmount:     false,
		AmountInSat:           9999,
		FeeRateInSatsPerVByte: 10,
	})
	if err != nil {
		t.Fatal(err)
	}
	if analysis.Status != AnalysisStatusUnpayable {
		t.Fatal("expected analysis to be unpayable")
	}
	if analysis.FeeTotalInSat != 2400 {
		t.Fatalf("expected fee to be %v, but got %v", 2400, analysis.FeeTotalInSat)
	}
	if analysis.FeeBumpInSat != 0 {
		t.Fatalf("expected fee bump to be %v, but got %v", 0, analysis.FeeBumpInSat)
	}
	if analysis.TotalInSat != 12399 {
		t.Fatalf("expected total to be %v, but got %v", 12399, analysis.TotalInSat)
	}

	analysis, err = analyzer.ToAddress(&PaymentToAddress{
		TakeFeeFromAmount:     false,
		AmountInSat:           9999,
		FeeRateInSatsPerVByte: 0.25,
	})

	if err != nil {
		t.Fatal(err)
	}
	if analysis.Status != AnalysisStatusUnpayable {
		t.Fatal("expected analysis to be unpayable")
	}
	if analysis.FeeTotalInSat != 60 {
		t.Fatalf("expected fee to be %v, but got %v", 60, analysis.FeeTotalInSat)
	}
	if analysis.FeeBumpInSat != 0 {
		t.Fatalf("expected fee bump to be %v, but got %v", 0, analysis.FeeBumpInSat)
	}
	if analysis.TotalInSat != 10059 {
		t.Fatalf("expected total to be %v, but got %v", 10059, analysis.TotalInSat)
	}
}

func TestAnalyzeOnChainValidAmountButUnpayableWithAnyFeeUsingTFFA(t *testing.T) {
	analyzer := NewPaymentAnalyzer(defaultFeeWindow, &NextTransactionSize{
		SizeProgression: []SizeForAmount{
			{
				AmountInSat: 600,
				SizeInVByte: 240,
			},
		},
		ExpectedDebtInSat: 0,
	},
		nil,
	)

	analysis, err := analyzer.ToAddress(&PaymentToAddress{
		TakeFeeFromAmount:     true,
		AmountInSat:           600,
		FeeRateInSatsPerVByte: 10,
	})
	if err != nil {
		t.Fatal(err)
	}
	if analysis.Status != AnalysisStatusUnpayable {
		t.Fatal("expected analysis to be unpayable")
	}
	if analysis.FeeTotalInSat != 2400 {
		t.Fatalf("expected fee to be %v, but got %v", 2400, analysis.FeeTotalInSat)
	}
	if analysis.FeeBumpInSat != 0 {
		t.Fatalf("expected fee bump to be %v, but got %v", 0, analysis.FeeBumpInSat)
	}
	if analysis.TotalInSat != 600 {
		t.Fatalf("expected total to be %v, but got %v", 600, analysis.TotalInSat)
	}

	analysis, err = analyzer.ToAddress(&PaymentToAddress{
		TakeFeeFromAmount:     true,
		AmountInSat:           600,
		FeeRateInSatsPerVByte: 0.25,
	})

	if err != nil {
		t.Fatal(err)
	}
	if analysis.Status != AnalysisStatusUnpayable {
		t.Fatal("expected analysis to be unpayable")
	}
	if analysis.FeeTotalInSat != 60 {
		t.Fatalf("expected fee to be %v, but got %v", 60, analysis.FeeTotalInSat)
	}
	if analysis.FeeBumpInSat != 0 {
		t.Fatalf("expected fee bump to be %v, but got %v", 0, analysis.FeeBumpInSat)
	}
	if analysis.AmountInSat != 540 {
		t.Fatalf("expected amount to be %v, but got %v", 540, analysis.TotalInSat)
	}
	if analysis.TotalInSat != 600 {
		t.Fatalf("expected total to be %v, but got %v", 600, analysis.TotalInSat)
	}
}

func TestAnalyzeOffChain(t *testing.T) {
	testCases := []struct {
		desc      string
		feeWindow *FeeWindow
		nts       *NextTransactionSize
		feeBump   []*FeeBumpFunction
		payment   *PaymentToInvoice
		expected  *PaymentAnalysis
		err       bool
	}{
		{
			desc: "swap with amount too small (zero funds)",
			payment: &PaymentToInvoice{
				TakeFeeFromAmount: false,
				AmountInSat:       0,
				SwapFees: &fees.SwapFees{
					OutputAmount:        0,
					DebtType:            fees.DebtTypeNone,
					DebtAmount:          0,
					RoutingFee:          0,
					OutputPadding:       0,
					ConfirmationsNeeded: 0,
				},
			},
			expected: &PaymentAnalysis{
				Status: AnalysisStatusAmountTooSmall,
			},
		},
		{
			desc: "swap with amount too small (negative funds)",
			payment: &PaymentToInvoice{
				TakeFeeFromAmount: false,
				AmountInSat:       -10,
				SwapFees: &fees.SwapFees{
					OutputAmount:        0,
					DebtType:            fees.DebtTypeNone,
					DebtAmount:          0,
					RoutingFee:          0,
					OutputPadding:       0,
					ConfirmationsNeeded: 0,
				},
			},
			expected: &PaymentAnalysis{
				Status: AnalysisStatusAmountTooSmall,
			},
		},
		{
			desc: "swap with amount greater than balance",
			payment: &PaymentToInvoice{
				TakeFeeFromAmount: false,
				AmountInSat:       5_000_000,
				SwapFees: &fees.SwapFees{
					OutputAmount:        5_000_000,
					DebtType:            fees.DebtTypeNone,
					DebtAmount:          0,
					RoutingFee:          0,
					OutputPadding:       0,
					ConfirmationsNeeded: 0,
				},
			},
			expected: &PaymentAnalysis{
				Status:     AnalysisStatusAmountGreaterThanBalance,
				TotalInSat: 5_000_000,
			},
		},
		{
			desc: "swap with integrity error",
			payment: &PaymentToInvoice{
				TakeFeeFromAmount: false,
				AmountInSat:       500_000,
				SwapFees: &fees.SwapFees{
					OutputAmount:        500_001,
					DebtType:            fees.DebtTypeNone,
					DebtAmount:          0,
					RoutingFee:          0,
					OutputPadding:       0,
					ConfirmationsNeeded: 0,
				},
			},
			err: true,
		},
		{
			desc: "swap COLLECT with integrity error",
			nts: &NextTransactionSize{
				SizeProgression: []SizeForAmount{
					{
						AmountInSat: 5_000_000,
						SizeInVByte: 240,
					},
				},
				ExpectedDebtInSat: 1000,
			},
			payment: &PaymentToInvoice{
				TakeFeeFromAmount: false,
				AmountInSat:       500_000,
				SwapFees: &fees.SwapFees{
					OutputAmount:        501_001,
					DebtType:            fees.DebtTypeCollect,
					DebtAmount:          1000,
					RoutingFee:          0,
					OutputPadding:       0,
					ConfirmationsNeeded: 0,
				},
			},
			err: true,
		},
		{
			desc: "swap with valid amount with no routing fee",
			nts: &NextTransactionSize{
				SizeProgression: []SizeForAmount{
					{
						AmountInSat: 10_000,
						SizeInVByte: 240,
					},
				},
				ExpectedDebtInSat: 0,
			},
			payment: &PaymentToInvoice{
				TakeFeeFromAmount: false,
				AmountInSat:       100,
				SwapFees: &fees.SwapFees{
					OutputAmount:        1100,
					DebtType:            fees.DebtTypeNone,
					DebtAmount:          0,
					RoutingFee:          0,
					OutputPadding:       1000,
					ConfirmationsNeeded: 0,
				},
			},
			expected: &PaymentAnalysis{
				Status:        AnalysisStatusOk,
				AmountInSat:   100,
				FeeTotalInSat: 240,
				TotalInSat:    1340,
				SwapFees: &fees.SwapFees{
					OutputAmount:        1100,
					DebtType:            fees.DebtTypeNone,
					DebtAmount:          0,
					RoutingFee:          0,
					OutputPadding:       1000,
					ConfirmationsNeeded: 0,
				},
			},
		},
		{
			desc: "swap with valid amount",
			nts: &NextTransactionSize{
				SizeProgression: []SizeForAmount{
					{
						AmountInSat: 10_000,
						SizeInVByte: 240,
					},
				},
				ExpectedDebtInSat: 0,
			},
			payment: &PaymentToInvoice{
				TakeFeeFromAmount: false,
				AmountInSat:       100,
				SwapFees: &fees.SwapFees{
					OutputAmount:        1101,
					DebtType:            fees.DebtTypeNone,
					DebtAmount:          0,
					RoutingFee:          1,
					OutputPadding:       1000,
					ConfirmationsNeeded: 0,
				},
			},
			expected: &PaymentAnalysis{
				Status:        AnalysisStatusOk,
				AmountInSat:   100,
				FeeTotalInSat: 240,
				TotalInSat:    1341,
				SwapFees: &fees.SwapFees{
					OutputAmount:        1101,
					DebtType:            fees.DebtTypeNone,
					DebtAmount:          0,
					RoutingFee:          1,
					OutputPadding:       1000,
					ConfirmationsNeeded: 0,
				},
			},
		},
		{
			desc: "swap with valid amount and fee bump",
			nts: &NextTransactionSize{
				SizeProgression: []SizeForAmount{
					{
						AmountInSat: 10_000,
						SizeInVByte: 240,
						Outpoint:    "0437cd7f8525ceed2324359c2d0ba26006d92d856a9c20fa0241106ee5a597c4:0",
						UtxoStatus:  UtxosStatusUnconfirmed,
					},
				},
				ExpectedDebtInSat: 0,
			},
			feeBump: []*FeeBumpFunction{
				&FeeBumpFunction{
					[]*PartialLinearFunction{
						partialLinearFunction,
					}},
			},
			payment: &PaymentToInvoice{
				TakeFeeFromAmount: false,
				AmountInSat:       100,
				SwapFees: &fees.SwapFees{
					OutputAmount:        1101,
					DebtType:            fees.DebtTypeNone,
					DebtAmount:          0,
					RoutingFee:          1,
					OutputPadding:       1000,
					ConfirmationsNeeded: 0,
				},
			},
			expected: &PaymentAnalysis{
				Status:        AnalysisStatusOk,
				AmountInSat:   100,
				FeeTotalInSat: 342,
				FeeBumpInSat:  102,
				TotalInSat:    1443,
				SwapFees: &fees.SwapFees{
					OutputAmount:        1101,
					DebtType:            fees.DebtTypeNone,
					DebtAmount:          0,
					RoutingFee:          1,
					OutputPadding:       1000,
					ConfirmationsNeeded: 0,
				},
			},
		},
		{
			desc: "swap with valid amount with 1-conf",
			nts: &NextTransactionSize{
				SizeProgression: []SizeForAmount{
					{
						AmountInSat: 10_000,
						SizeInVByte: 240,
					},
				},
				ExpectedDebtInSat: 0,
			},
			payment: &PaymentToInvoice{
				TakeFeeFromAmount: false,
				AmountInSat:       100,
				SwapFees: &fees.SwapFees{
					OutputAmount:        1101,
					DebtType:            fees.DebtTypeNone,
					DebtAmount:          0,
					RoutingFee:          1,
					OutputPadding:       1000,
					ConfirmationsNeeded: 1,
				},
			},
			expected: &PaymentAnalysis{
				Status:        AnalysisStatusOk,
				AmountInSat:   100,
				FeeTotalInSat: 2400,
				TotalInSat:    3501,
				SwapFees: &fees.SwapFees{
					OutputAmount:        1101,
					DebtType:            fees.DebtTypeNone,
					DebtAmount:          0,
					RoutingFee:          1,
					OutputPadding:       1000,
					ConfirmationsNeeded: 1,
				},
			},
		},
		{
			desc: "swap with valid amount with 1-conf and fee bump",
			nts: &NextTransactionSize{
				SizeProgression: []SizeForAmount{
					{
						AmountInSat: 10_000,
						SizeInVByte: 240,
						Outpoint:    "0437cd7f8525ceed2324359c2d0ba26006d92d856a9c20fa0241106ee5a597c4:0",
						UtxoStatus:  UtxosStatusUnconfirmed,
					},
				},
				ExpectedDebtInSat: 0,
			},
			feeBump: []*FeeBumpFunction{
				&FeeBumpFunction{
					[]*PartialLinearFunction{
						partialLinearFunction,
					}},
			},
			payment: &PaymentToInvoice{
				TakeFeeFromAmount: false,
				AmountInSat:       100,
				SwapFees: &fees.SwapFees{
					OutputAmount:        1101,
					DebtType:            fees.DebtTypeNone,
					DebtAmount:          0,
					RoutingFee:          1,
					OutputPadding:       1000,
					ConfirmationsNeeded: 1,
				},
			},
			expected: &PaymentAnalysis{
				Status:        AnalysisStatusOk,
				AmountInSat:   100,
				FeeTotalInSat: 2520,
				FeeBumpInSat:  120,
				TotalInSat:    3621,
				SwapFees: &fees.SwapFees{
					OutputAmount:        1101,
					DebtType:            fees.DebtTypeNone,
					DebtAmount:          0,
					RoutingFee:          1,
					OutputPadding:       1000,
					ConfirmationsNeeded: 1,
				},
			},
		},
		{
			desc: "swap with valid FIXED amount using TFFA",
			nts: &NextTransactionSize{
				SizeProgression: []SizeForAmount{
					{
						AmountInSat: 10_000,
						SizeInVByte: 240,
					},
				},
				ExpectedDebtInSat: 0,
			},
			payment: &PaymentToInvoice{
				TakeFeeFromAmount: true,
				AmountInSat:       10_000,
				SwapFees: &fees.SwapFees{
					OutputAmount:        10_000,
					DebtType:            fees.DebtTypeNone,
					DebtAmount:          0,
					RoutingFee:          0,
					OutputPadding:       0,
					ConfirmationsNeeded: 0,
				},
			},
			err: true,
		},
		{
			desc: "swap valid amount but unpayable because of fee",
			nts: &NextTransactionSize{
				SizeProgression: []SizeForAmount{
					{
						AmountInSat: 10_000,
						SizeInVByte: 240,
					},
				},
				ExpectedDebtInSat: 0,
			},
			payment: &PaymentToInvoice{
				TakeFeeFromAmount: false,
				AmountInSat:       500,
				SwapFees: &fees.SwapFees{
					OutputAmount:        10000,
					DebtType:            fees.DebtTypeNone,
					DebtAmount:          0,
					RoutingFee:          1000,
					OutputPadding:       8500,
					ConfirmationsNeeded: 0,
				},
			},
			expected: &PaymentAnalysis{
				Status:        AnalysisStatusUnpayable,
				AmountInSat:   500,
				FeeTotalInSat: 240,
				TotalInSat:    10240,
				SwapFees: &fees.SwapFees{
					OutputAmount:        10000,
					DebtType:            fees.DebtTypeNone,
					DebtAmount:          0,
					RoutingFee:          1000,
					OutputPadding:       8500,
					ConfirmationsNeeded: 0,
				},
			},
		},
		{
			desc: "swap valid amount but unpayable because of output padding",
			nts: &NextTransactionSize{
				SizeProgression: []SizeForAmount{
					{
						AmountInSat: 10_000,
						SizeInVByte: 240,
					},
				},
				ExpectedDebtInSat: 0,
			},
			payment: &PaymentToInvoice{
				TakeFeeFromAmount: false,
				AmountInSat:       500,
				SwapFees: &fees.SwapFees{
					OutputAmount:        11000,
					DebtType:            fees.DebtTypeNone,
					DebtAmount:          0,
					RoutingFee:          1000,
					OutputPadding:       9500,
					ConfirmationsNeeded: 0,
				},
			},
			expected: &PaymentAnalysis{
				Status:        AnalysisStatusUnpayable,
				AmountInSat:   500,
				FeeTotalInSat: 240,
				TotalInSat:    11240,
				SwapFees: &fees.SwapFees{
					OutputAmount:        11000,
					DebtType:            fees.DebtTypeNone,
					DebtAmount:          0,
					RoutingFee:          1000,
					OutputPadding:       9500,
					ConfirmationsNeeded: 0,
				},
			},
		},
		{
			desc: "swap LEND success",
			payment: &PaymentToInvoice{
				TakeFeeFromAmount: false,
				AmountInSat:       100,
				SwapFees: &fees.SwapFees{
					OutputAmount:        0,
					DebtType:            fees.DebtTypeLend,
					DebtAmount:          100,
					RoutingFee:          1,
					OutputPadding:       0,
					ConfirmationsNeeded: 0,
				},
			},
			expected: &PaymentAnalysis{
				Status:        AnalysisStatusOk,
				AmountInSat:   100,
				FeeTotalInSat: 0,
				TotalInSat:    101,
				SwapFees: &fees.SwapFees{
					OutputAmount:        0,
					DebtType:            fees.DebtTypeLend,
					DebtAmount:          100,
					RoutingFee:          1,
					OutputPadding:       0,
					ConfirmationsNeeded: 0,
				},
			},
		},
		{
			desc: "swap LEND success with no routing fee",
			payment: &PaymentToInvoice{
				TakeFeeFromAmount: false,
				AmountInSat:       100,
				SwapFees: &fees.SwapFees{
					OutputAmount:        0,
					DebtType:            fees.DebtTypeLend,
					DebtAmount:          100,
					RoutingFee:          0,
					OutputPadding:       0,
					ConfirmationsNeeded: 0,
				},
			},
			expected: &PaymentAnalysis{
				Status:        AnalysisStatusOk,
				AmountInSat:   100,
				FeeTotalInSat: 0,
				TotalInSat:    100,
				SwapFees: &fees.SwapFees{
					OutputAmount:        0,
					DebtType:            fees.DebtTypeLend,
					DebtAmount:          100,
					RoutingFee:          0,
					OutputPadding:       0,
					ConfirmationsNeeded: 0,
				},
			},
		},
		{
			desc: "swap LEND amount greater than balance",
			nts: &NextTransactionSize{
				SizeProgression: []SizeForAmount{
					{
						AmountInSat: 1_000,
						SizeInVByte: 240,
					},
				},
				ExpectedDebtInSat: 900,
			},
			payment: &PaymentToInvoice{
				TakeFeeFromAmount: false,
				AmountInSat:       200,
				SwapFees: &fees.SwapFees{
					OutputAmount:        0,
					DebtType:            fees.DebtTypeLend,
					DebtAmount:          200,
					RoutingFee:          0,
					OutputPadding:       0,
					ConfirmationsNeeded: 0,
				},
			},
			expected: &PaymentAnalysis{
				Status:     AnalysisStatusAmountGreaterThanBalance,
				TotalInSat: 200,
			},
		},
		{
			desc: "swap LEND unpayable",
			nts: &NextTransactionSize{
				SizeProgression: []SizeForAmount{
					{
						AmountInSat: 1_000,
						SizeInVByte: 240,
					},
				},
				ExpectedDebtInSat: 900,
			},
			payment: &PaymentToInvoice{
				TakeFeeFromAmount: false,
				AmountInSat:       100,
				SwapFees: &fees.SwapFees{
					OutputAmount:        0,
					DebtType:            fees.DebtTypeLend,
					DebtAmount:          100,
					RoutingFee:          10,
					OutputPadding:       0,
					ConfirmationsNeeded: 0,
				},
			},
			expected: &PaymentAnalysis{
				Status:        AnalysisStatusUnpayable,
				AmountInSat:   100,
				FeeTotalInSat: 0,
				TotalInSat:    110,
				SwapFees: &fees.SwapFees{
					OutputAmount:        0,
					DebtType:            fees.DebtTypeLend,
					DebtAmount:          100,
					RoutingFee:          10,
					OutputPadding:       0,
					ConfirmationsNeeded: 0,
				},
			},
		},
		{
			desc: "swap full COLLECT success with no routing fee",
			nts: &NextTransactionSize{
				SizeProgression: []SizeForAmount{
					{
						AmountInSat: 10401,
						SizeInVByte: 240,
					},
				},
				ExpectedDebtInSat: 3000,
			},
			payment: &PaymentToInvoice{
				TakeFeeFromAmount: false,
				AmountInSat:       5000,
				SwapFees: &fees.SwapFees{
					OutputAmount:        8000,
					DebtType:            fees.DebtTypeCollect,
					DebtAmount:          3000,
					RoutingFee:          0,
					OutputPadding:       0,
					ConfirmationsNeeded: 0,
				},
			},
			expected: &PaymentAnalysis{
				Status:        AnalysisStatusOk,
				AmountInSat:   5000,
				FeeTotalInSat: 240,
				TotalInSat:    5240,
				SwapFees: &fees.SwapFees{
					OutputAmount:        8000,
					DebtType:            fees.DebtTypeCollect,
					DebtAmount:          3000,
					RoutingFee:          0,
					OutputPadding:       0,
					ConfirmationsNeeded: 0,
				},
			},
		},
		{
			desc: "swap full COLLECT success",
			nts: &NextTransactionSize{
				SizeProgression: []SizeForAmount{
					{
						AmountInSat: 10401,
						SizeInVByte: 240,
					},
				},
				ExpectedDebtInSat: 3000,
			},
			payment: &PaymentToInvoice{
				TakeFeeFromAmount: false,
				AmountInSat:       5000,
				SwapFees: &fees.SwapFees{
					OutputAmount:        8001,
					DebtType:            fees.DebtTypeCollect,
					DebtAmount:          3000,
					RoutingFee:          1,
					OutputPadding:       0,
					ConfirmationsNeeded: 0,
				},
			},
			expected: &PaymentAnalysis{
				Status:        AnalysisStatusOk,
				AmountInSat:   5000,
				FeeTotalInSat: 240,
				TotalInSat:    5241,
				SwapFees: &fees.SwapFees{
					OutputAmount:        8001,
					DebtType:            fees.DebtTypeCollect,
					DebtAmount:          3000,
					RoutingFee:          1,
					OutputPadding:       0,
					ConfirmationsNeeded: 0,
				},
			},
		},
		{
			desc: "swap full COLLECT success with 1-conf",
			nts: &NextTransactionSize{
				SizeProgression: []SizeForAmount{
					{
						AmountInSat: 10401,
						SizeInVByte: 240,
					},
				},
				ExpectedDebtInSat: 3000,
			},
			payment: &PaymentToInvoice{
				TakeFeeFromAmount: false,
				AmountInSat:       5000,
				SwapFees: &fees.SwapFees{
					OutputAmount:        8001,
					DebtType:            fees.DebtTypeCollect,
					DebtAmount:          3000,
					RoutingFee:          1,
					OutputPadding:       0,
					ConfirmationsNeeded: 1,
				},
			},
			expected: &PaymentAnalysis{
				Status:        AnalysisStatusOk,
				AmountInSat:   5000,
				FeeTotalInSat: 2400,
				TotalInSat:    7401,
				SwapFees: &fees.SwapFees{
					OutputAmount:        8001,
					DebtType:            fees.DebtTypeCollect,
					DebtAmount:          3000,
					RoutingFee:          1,
					OutputPadding:       0,
					ConfirmationsNeeded: 1,
				},
			},
		},
		{
			desc: "swap COLLECT output amount greater than balance (aka unpayable)",
			nts: &NextTransactionSize{
				SizeProgression: []SizeForAmount{
					{
						AmountInSat: 10401,
						SizeInVByte: 240,
					},
				},
				ExpectedDebtInSat: 3000,
			},
			payment: &PaymentToInvoice{
				TakeFeeFromAmount: false,
				AmountInSat:       7400,
				SwapFees: &fees.SwapFees{
					OutputAmount:        10500,
					DebtType:            fees.DebtTypeCollect,
					DebtAmount:          3000,
					RoutingFee:          100,
					OutputPadding:       0,
					ConfirmationsNeeded: 0,
				},
			},
			expected: &PaymentAnalysis{
				Status:        AnalysisStatusUnpayable,
				AmountInSat:   7400,
				FeeTotalInSat: 240,
				TotalInSat:    7740,
				SwapFees: &fees.SwapFees{
					OutputAmount:        10500,
					DebtType:            fees.DebtTypeCollect,
					DebtAmount:          3000,
					RoutingFee:          100,
					OutputPadding:       0,
					ConfirmationsNeeded: 0,
				},
			},
		},
		{
			desc: "swap COLLECT amount plus fee greater than balance (aka unpayable)",
			nts: &NextTransactionSize{
				SizeProgression: []SizeForAmount{
					{
						AmountInSat: 10500,
						SizeInVByte: 240,
					},
				},
				ExpectedDebtInSat: 3000,
			},
			payment: &PaymentToInvoice{
				TakeFeeFromAmount: false,
				AmountInSat:       7400,
				SwapFees: &fees.SwapFees{
					OutputAmount:        10500,
					DebtType:            fees.DebtTypeCollect,
					DebtAmount:          3000,
					RoutingFee:          100,
					OutputPadding:       0,
					ConfirmationsNeeded: 0,
				},
			},
			expected: &PaymentAnalysis{
				Status:        AnalysisStatusUnpayable,
				AmountInSat:   7400,
				FeeTotalInSat: 240,
				TotalInSat:    7740,
				SwapFees: &fees.SwapFees{
					OutputAmount:        10500,
					DebtType:            fees.DebtTypeCollect,
					DebtAmount:          3000,
					RoutingFee:          100,
					OutputPadding:       0,
					ConfirmationsNeeded: 0,
				},
			},
		},
		{
			desc: "swap partial COLLECT success with no routing fee",
			nts: &NextTransactionSize{
				SizeProgression: []SizeForAmount{
					{
						AmountInSat: 20401,
						SizeInVByte: 240,
					},
				},
				ExpectedDebtInSat: 8000,
			},
			payment: &PaymentToInvoice{
				TakeFeeFromAmount: false,
				AmountInSat:       12160,
				SwapFees: &fees.SwapFees{
					OutputAmount:        14160,
					DebtType:            fees.DebtTypeCollect,
					DebtAmount:          2000,
					RoutingFee:          0,
					OutputPadding:       0,
					ConfirmationsNeeded: 0,
				},
			},
			expected: &PaymentAnalysis{
				Status:        AnalysisStatusOk,
				AmountInSat:   12160,
				FeeTotalInSat: 240,
				TotalInSat:    12400,
				SwapFees: &fees.SwapFees{
					OutputAmount:        14160,
					DebtType:            fees.DebtTypeCollect,
					DebtAmount:          2000,
					RoutingFee:          0,
					OutputPadding:       0,
					ConfirmationsNeeded: 0,
				},
			},
		},
		{
			desc: "swap partial COLLECT success",
			nts: &NextTransactionSize{
				SizeProgression: []SizeForAmount{
					{
						AmountInSat: 20401,
						SizeInVByte: 240,
					},
				},
				ExpectedDebtInSat: 8000,
			},
			payment: &PaymentToInvoice{
				TakeFeeFromAmount: false,
				AmountInSat:       12160,
				SwapFees: &fees.SwapFees{
					OutputAmount:        14161,
					DebtType:            fees.DebtTypeCollect,
					DebtAmount:          2000,
					RoutingFee:          1,
					OutputPadding:       0,
					ConfirmationsNeeded: 0,
				},
			},
			expected: &PaymentAnalysis{
				Status:        AnalysisStatusOk,
				AmountInSat:   12160,
				FeeTotalInSat: 240,
				TotalInSat:    12401,
				SwapFees: &fees.SwapFees{
					OutputAmount:        14161,
					DebtType:            fees.DebtTypeCollect,
					DebtAmount:          2000,
					RoutingFee:          1,
					OutputPadding:       0,
					ConfirmationsNeeded: 0,
				},
			},
		},
		{
			desc: "swap partial COLLECT for unpayable amount",
			nts: &NextTransactionSize{
				SizeProgression: []SizeForAmount{
					{
						AmountInSat: 20401,
						SizeInVByte: 240,
					},
				},
				ExpectedDebtInSat: 8000,
			},
			payment: &PaymentToInvoice{
				TakeFeeFromAmount: false,
				AmountInSat:       12401,
				SwapFees: &fees.SwapFees{
					OutputAmount:        14401,
					DebtType:            fees.DebtTypeCollect,
					DebtAmount:          2000,
					RoutingFee:          0,
					OutputPadding:       0,
					ConfirmationsNeeded: 0,
				},
			},
			expected: &PaymentAnalysis{
				Status:        AnalysisStatusUnpayable,
				AmountInSat:   12401,
				FeeTotalInSat: 240,
				TotalInSat:    12641,
				SwapFees: &fees.SwapFees{
					OutputAmount:        14401,
					DebtType:            fees.DebtTypeCollect,
					DebtAmount:          2000,
					RoutingFee:          0,
					OutputPadding:       0,
					ConfirmationsNeeded: 0,
				},
			},
		},
		{
			desc: "swap amountless invoice as LEND swap success",
			nts: &NextTransactionSize{
				SizeProgression: []SizeForAmount{
					{
						AmountInSat: 20401,
						SizeInVByte: 240,
					},
				},
				ExpectedDebtInSat: 3000,
			},
			payment: &PaymentToInvoice{
				TakeFeeFromAmount: false,
				AmountInSat:       12401,
				BestRouteFees: []fees.BestRouteFees{
					{
						MaxCapacity:              100000,
						FeeProportionalMillionth: 1,
						FeeBase:                  10,
					},
				},
				FundingOutputPolicies: &fees.FundingOutputPolicies{
					MaximumDebt:       1000000,
					PotentialCollect:  0,
					MaxAmountFor0Conf: 1000000,
				},
			},
			expected: &PaymentAnalysis{
				Status:        AnalysisStatusOk,
				AmountInSat:   12401,
				FeeTotalInSat: 0,
				TotalInSat:    12411,
				SwapFees: &fees.SwapFees{
					OutputAmount:        0,
					DebtType:            fees.DebtTypeLend,
					DebtAmount:          12411,
					RoutingFee:          10,
					OutputPadding:       0,
					ConfirmationsNeeded: 0,
				},
			},
		},
		{
			desc: "swap amountless invoice as COLLECT swap success",
			nts: &NextTransactionSize{
				SizeProgression: []SizeForAmount{
					{
						AmountInSat: 20401,
						SizeInVByte: 240,
					},
				},
				ExpectedDebtInSat: 3000,
			},
			payment: &PaymentToInvoice{
				TakeFeeFromAmount: false,
				AmountInSat:       12401,
				BestRouteFees: []fees.BestRouteFees{
					{
						MaxCapacity:              100000,
						FeeProportionalMillionth: 1,
						FeeBase:                  10,
					},
				},
				FundingOutputPolicies: &fees.FundingOutputPolicies{
					MaximumDebt:       100,
					PotentialCollect:  1010,
					MaxAmountFor0Conf: 1000000,
				},
			},
			expected: &PaymentAnalysis{
				Status:        AnalysisStatusOk,
				AmountInSat:   12401,
				FeeTotalInSat: 240,
				TotalInSat:    12651,
				SwapFees: &fees.SwapFees{
					OutputAmount:        13421,
					DebtType:            fees.DebtTypeCollect,
					DebtAmount:          1010,
					RoutingFee:          10,
					OutputPadding:       0,
					ConfirmationsNeeded: 0,
				},
			},
		},
		{
			desc: "swap amountless invoice as NON DEBT swap success",
			nts: &NextTransactionSize{
				SizeProgression: []SizeForAmount{
					{
						AmountInSat: 20401,
						SizeInVByte: 240,
					},
				},
				ExpectedDebtInSat: 3000,
			},
			payment: &PaymentToInvoice{
				TakeFeeFromAmount: false,
				AmountInSat:       12401,
				BestRouteFees: []fees.BestRouteFees{
					{
						MaxCapacity:              100000,
						FeeProportionalMillionth: 1,
						FeeBase:                  10,
					},
				},
				FundingOutputPolicies: &fees.FundingOutputPolicies{
					MaximumDebt:       0,
					PotentialCollect:  0,
					MaxAmountFor0Conf: 1000000,
				},
			},
			expected: &PaymentAnalysis{
				Status:        AnalysisStatusOk,
				AmountInSat:   12401,
				FeeTotalInSat: 240,
				TotalInSat:    12651,
				SwapFees: &fees.SwapFees{
					OutputAmount:        12411,
					DebtType:            fees.DebtTypeNone,
					DebtAmount:          0,
					RoutingFee:          10,
					OutputPadding:       0,
					ConfirmationsNeeded: 0,
				},
			},
		},
		{
			desc: "swap amountless invoice one-conf",
			nts: &NextTransactionSize{
				SizeProgression: []SizeForAmount{
					{
						AmountInSat: 20401,
						SizeInVByte: 240,
					},
				},
				ExpectedDebtInSat: 3000,
			},
			payment: &PaymentToInvoice{
				TakeFeeFromAmount: false,
				AmountInSat:       12401,
				BestRouteFees: []fees.BestRouteFees{
					{
						MaxCapacity:              100000,
						FeeProportionalMillionth: 1,
						FeeBase:                  10,
					},
				},
				FundingOutputPolicies: &fees.FundingOutputPolicies{
					MaximumDebt:       0,
					PotentialCollect:  0,
					MaxAmountFor0Conf: 0,
				},
			},
			expected: &PaymentAnalysis{
				Status:        AnalysisStatusOk,
				AmountInSat:   12401,
				FeeTotalInSat: 2400,
				TotalInSat:    14811,
				SwapFees: &fees.SwapFees{
					OutputAmount:        12411,
					DebtType:            fees.DebtTypeNone,
					DebtAmount:          0,
					RoutingFee:          10,
					OutputPadding:       0,
					ConfirmationsNeeded: 1,
				},
			},
		},
		{
			desc:      "swap amountless invoice TFFA one-conf",
			feeWindow: feeWindowFailureTests,
			nts: &NextTransactionSize{
				SizeProgression: []SizeForAmount{
					{
						AmountInSat: 369_310,
						SizeInVByte: 253,
					},
				},
				ExpectedDebtInSat: 26876,
			},
			payment: &PaymentToInvoice{
				TakeFeeFromAmount: true,
				AmountInSat:       342434,
				BestRouteFees: []fees.BestRouteFees{
					{
						MaxCapacity:              1000000,
						FeeProportionalMillionth: 1051,
						FeeBase:                  4,
					},
				},
				FundingOutputPolicies: &fees.FundingOutputPolicies{
					MaximumDebt:       123124,
					PotentialCollect:  20686,
					MaxAmountFor0Conf: 0,
				},
			},
			expected: &PaymentAnalysis{
				Status:        AnalysisStatusOk,
				AmountInSat:   316798,
				FeeTotalInSat: 25300,
				TotalInSat:    342434,
				SwapFees: &fees.SwapFees{
					OutputAmount:        337820,
					DebtType:            fees.DebtTypeCollect,
					DebtAmount:          20686,
					RoutingFee:          336,
					OutputPadding:       0,
					ConfirmationsNeeded: 1,
				},
			},
		},
		{
			desc:      "swap amountless invoice TFFA with invalid amount",
			feeWindow: feeWindowFailureTests,
			nts: &NextTransactionSize{
				SizeProgression: []SizeForAmount{
					{
						AmountInSat: 36_931,
						SizeInVByte: 253,
					},
				},
				ExpectedDebtInSat: 26876,
			},
			payment: &PaymentToInvoice{
				TakeFeeFromAmount: true,
				AmountInSat:       10054,
				BestRouteFees: []fees.BestRouteFees{
					{
						MaxCapacity:              100000,
						FeeProportionalMillionth: 1051,
						FeeBase:                  4,
					},
				},
				FundingOutputPolicies: &fees.FundingOutputPolicies{
					MaximumDebt:       123124,
					PotentialCollect:  20686,
					MaxAmountFor0Conf: 193437,
				},
			},
			err: true,
		},
		{
			desc: "swap amountless invoice TFFA cant pay combined fee",
			feeWindow: &FeeWindow{
				TargetedFees: func() map[uint]float64 {
					fees := make(map[uint]float64)
					fees[1] = 100.0
					fees[5] = 50.0
					fees[10] = 3.1
					return fees
				}(),
			},
			nts: &NextTransactionSize{
				SizeProgression: []SizeForAmount{
					{
						AmountInSat: 800,
						SizeInVByte: 253,
					},
				},
				ExpectedDebtInSat: 0,
			},
			payment: &PaymentToInvoice{
				TakeFeeFromAmount: true,
				AmountInSat:       800,
				BestRouteFees: []fees.BestRouteFees{
					{
						MaxCapacity:              100000,
						FeeProportionalMillionth: 1051,
						FeeBase:                  40,
					},
				},
				FundingOutputPolicies: &fees.FundingOutputPolicies{
					MaximumDebt:       123124,
					PotentialCollect:  20686,
					MaxAmountFor0Conf: 193437,
				},
			},
			expected: &PaymentAnalysis{
				Status:        AnalysisStatusUnpayable,
				AmountInSat:   0,
				FeeTotalInSat: 785,
				TotalInSat:    1585,
			},
		},
		{
			desc:      "swap amountless invoice TFFA (failure 1)",
			feeWindow: feeWindowFailureTests,
			nts: &NextTransactionSize{
				SizeProgression: []SizeForAmount{
					{
						AmountInSat: 36_931,
						SizeInVByte: 253,
					},
				},
				ExpectedDebtInSat: 26876,
			},
			payment: &PaymentToInvoice{
				TakeFeeFromAmount: true,
				AmountInSat:       10055,
				BestRouteFees: []fees.BestRouteFees{
					{
						MaxCapacity:              100000,
						FeeProportionalMillionth: 1051,
						FeeBase:                  4,
					},
				},
				FundingOutputPolicies: &fees.FundingOutputPolicies{
					MaximumDebt:       123124,
					PotentialCollect:  20686,
					MaxAmountFor0Conf: 193437,
				},
			},
			expected: &PaymentAnalysis{
				Status:        AnalysisStatusOk,
				AmountInSat:   9977,
				FeeTotalInSat: 64,
				TotalInSat:    10055,
				SwapFees: &fees.SwapFees{
					OutputAmount:        30677,
					DebtType:            fees.DebtTypeCollect,
					DebtAmount:          20686,
					RoutingFee:          14,
					OutputPadding:       0,
					ConfirmationsNeeded: 0,
				},
			},
		},
		{
			desc:      "swap amountless invoice TFFA (failure 2)",
			feeWindow: feeWindowFailureTests,
			nts: &NextTransactionSize{
				SizeProgression: []SizeForAmount{
					{
						AmountInSat: 83880,
						SizeInVByte: 495,
					},
				},
				ExpectedDebtInSat: 73590,
			},
			payment: &PaymentToInvoice{
				TakeFeeFromAmount: true,
				AmountInSat:       10290,
				BestRouteFees: []fees.BestRouteFees{
					{
						MaxCapacity:              100000,
						FeeProportionalMillionth: 297,
						FeeBase:                  4,
					},
				},
				FundingOutputPolicies: &fees.FundingOutputPolicies{
					MaximumDebt:       76410,
					PotentialCollect:  33292,
					MaxAmountFor0Conf: 254235,
				},
			},
			expected: &PaymentAnalysis{
				Status:        AnalysisStatusOk,
				AmountInSat:   10159,
				FeeTotalInSat: 124,
				TotalInSat:    10290,
				SwapFees: &fees.SwapFees{
					OutputAmount:        43458,
					DebtType:            fees.DebtTypeCollect,
					DebtAmount:          33292,
					RoutingFee:          7,
					OutputPadding:       0,
					ConfirmationsNeeded: 0,
				},
			},
		},
		{
			desc:      "swap amountless invoice TFFA (failure 3)",
			feeWindow: feeWindowFailureTests,
			nts: &NextTransactionSize{
				SizeProgression: []SizeForAmount{
					{
						AmountInSat: 1597,
						SizeInVByte: 391,
					},
				},
				ExpectedDebtInSat: 1511,
			},
			payment: &PaymentToInvoice{
				TakeFeeFromAmount: true,
				AmountInSat:       86,
				BestRouteFees: []fees.BestRouteFees{
					{
						MaxCapacity:              100000,
						FeeProportionalMillionth: 875,
						FeeBase:                  2,
					},
				},
				FundingOutputPolicies: &fees.FundingOutputPolicies{
					MaximumDebt:       148489,
					PotentialCollect:  759,
					MaxAmountFor0Conf: 163704,
				},
			},
			expected: &PaymentAnalysis{
				Status:        AnalysisStatusUnpayable,
				FeeTotalInSat: 98,
				TotalInSat:    86,
			},
		},
		{
			desc:      "swap amountless invoice TFFA (failure 4)",
			feeWindow: feeWindowFailureTests,
			nts: &NextTransactionSize{
				SizeProgression: []SizeForAmount{
					{
						AmountInSat: 1016,
						SizeInVByte: 409,
					},
				},
				ExpectedDebtInSat: 776,
			},
			payment: &PaymentToInvoice{
				TakeFeeFromAmount: true,
				AmountInSat:       240,
				BestRouteFees: []fees.BestRouteFees{
					{
						MaxCapacity:              100000,
						FeeProportionalMillionth: 468,
						FeeBase:                  4,
					},
				},
				FundingOutputPolicies: &fees.FundingOutputPolicies{
					MaximumDebt:       149224,
					PotentialCollect:  246,
					MaxAmountFor0Conf: 243320,
				},
			},
			expected: &PaymentAnalysis{
				Status:        AnalysisStatusUnpayable,
				AmountInSat:   133,
				FeeTotalInSat: 103,
				TotalInSat:    240,
				SwapFees: &fees.SwapFees{
					RoutingFee:    4,
					OutputPadding: 163,
					DebtType:      fees.DebtTypeCollect,
					DebtAmount:    246,
					OutputAmount:  546,
				},
			},
		},
		{
			desc:      "swap amountless invoice TFFA (failure 5)",
			feeWindow: feeWindowFailureTests,
			nts: &NextTransactionSize{
				SizeProgression: []SizeForAmount{
					{
						AmountInSat: 1843,
						SizeInVByte: 475,
					},
				},
				ExpectedDebtInSat: 458,
			},
			payment: &PaymentToInvoice{
				TakeFeeFromAmount: true,
				AmountInSat:       1385,
				BestRouteFees: []fees.BestRouteFees{
					{
						MaxCapacity:              100000,
						FeeProportionalMillionth: 1417,
						FeeBase:                  2,
					},
				},
				FundingOutputPolicies: &fees.FundingOutputPolicies{
					MaximumDebt:       149542,
					PotentialCollect:  222,
					MaxAmountFor0Conf: 223421,
				},
			},
			expected: &PaymentAnalysis{
				Status:        AnalysisStatusOk,
				AmountInSat:   1263,
				FeeTotalInSat: 119,
				TotalInSat:    1385,
				SwapFees: &fees.SwapFees{
					RoutingFee:    3,
					OutputPadding: 0,
					DebtType:      fees.DebtTypeCollect,
					DebtAmount:    222,
					OutputAmount:  1488,
				},
			},
		},
		{
			desc:      "swap amountless invoice TFFA (failure 6)",
			feeWindow: feeWindowFailureTests,
			nts: &NextTransactionSize{
				SizeProgression: []SizeForAmount{
					{
						AmountInSat: 6658,
						SizeInVByte: 262,
					},
				},
				ExpectedDebtInSat: 6539,
			},
			payment: &PaymentToInvoice{
				TakeFeeFromAmount: true,
				AmountInSat:       119,
				BestRouteFees: []fees.BestRouteFees{
					{
						MaxCapacity:              100000,
						FeeProportionalMillionth: 1991,
						FeeBase:                  1,
					},
				},
				FundingOutputPolicies: &fees.FundingOutputPolicies{
					MaximumDebt:       143461,
					PotentialCollect:  6422,
					MaxAmountFor0Conf: 254856,
				},
			},
			expected: &PaymentAnalysis{
				Status:        AnalysisStatusOk,
				AmountInSat:   52,
				FeeTotalInSat: 66,
				TotalInSat:    119,
				SwapFees: &fees.SwapFees{
					RoutingFee:    1,
					OutputPadding: 0,
					DebtType:      fees.DebtTypeCollect,
					DebtAmount:    6422,
					OutputAmount:  6475,
				},
			},
		},
		{
			desc:      "swap amountless invoice TFFA (failure 7)",
			feeWindow: feeWindowFailureTests,
			nts: &NextTransactionSize{
				SizeProgression: []SizeForAmount{
					{
						AmountInSat: 8169,
						SizeInVByte: 301,
					},
				},
				ExpectedDebtInSat: 5619,
			},
			payment: &PaymentToInvoice{
				TakeFeeFromAmount: true,
				AmountInSat:       2550,
				BestRouteFees: []fees.BestRouteFees{
					{
						MaxCapacity:              100000,
						FeeProportionalMillionth: 1009,
						FeeBase:                  2,
					},
				},
				FundingOutputPolicies: &fees.FundingOutputPolicies{
					MaximumDebt:       144381,
					PotentialCollect:  1140,
					MaxAmountFor0Conf: 210617,
				},
			},
			expected: &PaymentAnalysis{
				Status:        AnalysisStatusOk,
				AmountInSat:   2470,
				FeeTotalInSat: 76,
				TotalInSat:    2550,
				SwapFees: &fees.SwapFees{
					RoutingFee:    4,
					OutputPadding: 0,
					DebtType:      fees.DebtTypeCollect,
					DebtAmount:    1140,
					OutputAmount:  3614,
				},
			},
		},
		{
			desc:      "swap amountless invoice TFFA (failure 8)",
			feeWindow: feeWindowFailureTests,
			nts: &NextTransactionSize{
				SizeProgression: []SizeForAmount{
					{
						AmountInSat: 46785,
						SizeInVByte: 365,
					},
					{
						AmountInSat: 99948,
						SizeInVByte: 857,
					},
				},
				ExpectedDebtInSat: 40471,
			},
			payment: &PaymentToInvoice{
				TakeFeeFromAmount: true,
				AmountInSat:       59477,
				BestRouteFees: []fees.BestRouteFees{
					{
						MaxCapacity:              100000,
						FeeProportionalMillionth: 655,
						FeeBase:                  1,
					},
				},
				FundingOutputPolicies: &fees.FundingOutputPolicies{
					MaximumDebt:       109529,
					PotentialCollect:  13892,
					MaxAmountFor0Conf: 258399,
				},
			},
			expected: &PaymentAnalysis{
				Status:        AnalysisStatusOk,
				AmountInSat:   59223,
				FeeTotalInSat: 215,
				TotalInSat:    59477,
				SwapFees: &fees.SwapFees{
					RoutingFee:    39,
					OutputPadding: 0,
					DebtType:      fees.DebtTypeCollect,
					DebtAmount:    13892,
					OutputAmount:  73154,
				},
			},
		},
		{
			desc:      "swap amountless invoice TFFA (failure 9)",
			feeWindow: feeWindowFailureTests,
			nts: &NextTransactionSize{
				SizeProgression: []SizeForAmount{
					{
						AmountInSat: 10087,
						SizeInVByte: 430,
					},
					{
						AmountInSat: 156784,
						SizeInVByte: 851,
					},
				},
				ExpectedDebtInSat: 84117,
			},
			payment: &PaymentToInvoice{
				TakeFeeFromAmount: true,
				AmountInSat:       72667,
				BestRouteFees: []fees.BestRouteFees{
					{
						MaxCapacity:              100000,
						FeeProportionalMillionth: 1146,
						FeeBase:                  3,
					},
				},
				FundingOutputPolicies: &fees.FundingOutputPolicies{
					MaximumDebt:       65883,
					PotentialCollect:  77393,
					MaxAmountFor0Conf: 240440,
				},
			},
			expected: &PaymentAnalysis{
				Status:        AnalysisStatusOk,
				AmountInSat:   72369,
				FeeTotalInSat: 213,
				TotalInSat:    72667,
				SwapFees: &fees.SwapFees{
					RoutingFee:    85,
					OutputPadding: 0,
					DebtType:      fees.DebtTypeCollect,
					DebtAmount:    77393,
					OutputAmount:  149847,
				},
			},
		},
		{
			desc:      "swap amountless invoice TFFA (failure 10)",
			feeWindow: feeWindowFailureTests,
			nts: &NextTransactionSize{
				SizeProgression: []SizeForAmount{
					{
						AmountInSat: 117197,
						SizeInVByte: 333,
					},
				},
				ExpectedDebtInSat: 18225,
			},
			payment: &PaymentToInvoice{
				TakeFeeFromAmount: true,
				AmountInSat:       98972,
				BestRouteFees: []fees.BestRouteFees{
					{
						MaxCapacity:              100000,
						FeeProportionalMillionth: 890,
						FeeBase:                  1,
					},
				},
				FundingOutputPolicies: &fees.FundingOutputPolicies{
					MaximumDebt:       131775,
					PotentialCollect:  16109,
					MaxAmountFor0Conf: 182743,
				},
			},
			expected: &PaymentAnalysis{
				Status:        AnalysisStatusOk,
				AmountInSat:   98800,
				FeeTotalInSat: 84,
				TotalInSat:    98972,
				SwapFees: &fees.SwapFees{
					RoutingFee:    88,
					OutputPadding: 0,
					DebtType:      fees.DebtTypeCollect,
					DebtAmount:    16109,
					OutputAmount:  114997,
				},
			},
		},
		{
			desc:      "swap amountless invoice TFFA (failure 11)",
			feeWindow: feeWindowFailureTests,
			nts: &NextTransactionSize{
				SizeProgression: []SizeForAmount{
					{
						AmountInSat: 3165,
						SizeInVByte: 402,
					},
				},
				ExpectedDebtInSat: 2812,
			},
			payment: &PaymentToInvoice{
				TakeFeeFromAmount: true,
				AmountInSat:       353,
				BestRouteFees: []fees.BestRouteFees{
					{
						MaxCapacity:              100000,
						FeeProportionalMillionth: 1461,
						FeeBase:                  3,
					},
				},
				FundingOutputPolicies: &fees.FundingOutputPolicies{
					MaximumDebt:       147188,
					PotentialCollect:  222,
					MaxAmountFor0Conf: 164563,
				},
			},
			expected: &PaymentAnalysis{
				Status:        AnalysisStatusUnpayable,
				AmountInSat:   249,
				FeeTotalInSat: 101,
				TotalInSat:    353,
				SwapFees: &fees.SwapFees{
					RoutingFee:    3,
					OutputPadding: 72,
					DebtType:      fees.DebtTypeCollect,
					DebtAmount:    222,
					OutputAmount:  546,
				},
			},
		},
		{
			desc:      "swap amountless invoice TFFA (failure 11 bis)",
			feeWindow: feeWindowFailureTests,
			nts: &NextTransactionSize{
				SizeProgression: []SizeForAmount{
					{
						AmountInSat: 3165,
						SizeInVByte: 402,
					},
				},
				ExpectedDebtInSat: 2740,
			},
			payment: &PaymentToInvoice{
				TakeFeeFromAmount: true,
				AmountInSat:       425,
				BestRouteFees: []fees.BestRouteFees{
					{
						MaxCapacity:              100000,
						FeeProportionalMillionth: 1461,
						FeeBase:                  3,
					},
				},
				FundingOutputPolicies: &fees.FundingOutputPolicies{
					MaximumDebt:       147188,
					PotentialCollect:  222,
					MaxAmountFor0Conf: 164563,
				},
			},
			expected: &PaymentAnalysis{
				Status:        AnalysisStatusOk,
				AmountInSat:   321,
				FeeTotalInSat: 101,
				TotalInSat:    425,
				SwapFees: &fees.SwapFees{
					RoutingFee:    3,
					OutputPadding: 0,
					DebtType:      fees.DebtTypeCollect,
					DebtAmount:    222,
					OutputAmount:  546,
				},
			},
		},
		{
			desc:      "swap amountless invoice TFFA (failure 12)",
			feeWindow: feeWindowFailureTests,
			nts: &NextTransactionSize{
				SizeProgression: []SizeForAmount{
					{
						AmountInSat: 86343,
						SizeInVByte: 358,
					},
				},
				ExpectedDebtInSat: 32639,
			},
			payment: &PaymentToInvoice{
				TakeFeeFromAmount: true,
				AmountInSat:       53704,
				BestRouteFees: []fees.BestRouteFees{
					{
						MaxCapacity:              100000,
						FeeProportionalMillionth: 1457,
						FeeBase:                  2,
					},
				},
				FundingOutputPolicies: &fees.FundingOutputPolicies{
					MaximumDebt:       117361,
					PotentialCollect:  27161,
					MaxAmountFor0Conf: 287685,
				},
			},
			// In this scenario there's 1 sat that is lost and will be burn in fees to the miner
			expected: &PaymentAnalysis{
				Status:        AnalysisStatusOk,
				AmountInSat:   53534,
				FeeTotalInSat: 90,
				TotalInSat:    53704,
				SwapFees: &fees.SwapFees{
					RoutingFee:    79,
					OutputPadding: 0,
					DebtType:      fees.DebtTypeCollect,
					DebtAmount:    27161,
					OutputAmount:  80774,
				},
			},
		},
		{
			desc:      "swap amountless invoice TFFA (failure 13)",
			feeWindow: feeWindowFailureTests,
			nts: &NextTransactionSize{
				SizeProgression: []SizeForAmount{
					{
						AmountInSat: 500,
						SizeInVByte: 209,
					},
				},
				ExpectedDebtInSat: 0,
			},
			payment: &PaymentToInvoice{
				TakeFeeFromAmount: true,
				AmountInSat:       500,
				BestRouteFees: []fees.BestRouteFees{
					{
						MaxCapacity:              100000,
						FeeProportionalMillionth: 1000,
						FeeBase:                  1,
					},
				},
				FundingOutputPolicies: &fees.FundingOutputPolicies{
					MaximumDebt:       0,
					PotentialCollect:  0,
					MaxAmountFor0Conf: math.MaxInt64,
				},
			},
			expected: &PaymentAnalysis{
				Status:        AnalysisStatusUnpayable,
				AmountInSat:   446,
				FeeTotalInSat: 53,
				TotalInSat:    500,
				SwapFees: &fees.SwapFees{
					RoutingFee:    1,
					OutputPadding: 99,
					DebtType:      fees.DebtTypeNone,
					DebtAmount:    0,
					OutputAmount:  546,
				},
			},
		},
		{
			desc:      "swap amountless invoice TFFA with no best route for amount",
			feeWindow: feeWindowFailureTests,
			nts: &NextTransactionSize{
				SizeProgression: []SizeForAmount{
					{
						AmountInSat: 117197,
						SizeInVByte: 333,
					},
				},
				ExpectedDebtInSat: 18225,
			},
			payment: &PaymentToInvoice{
				TakeFeeFromAmount: true,
				AmountInSat:       98972,
				BestRouteFees: []fees.BestRouteFees{
					{
						MaxCapacity:              90000,
						FeeProportionalMillionth: 890,
						FeeBase:                  1,
					},
				},
				FundingOutputPolicies: &fees.FundingOutputPolicies{
					MaximumDebt:       131775,
					PotentialCollect:  16109,
					MaxAmountFor0Conf: 182743,
				},
			},
			expected: &PaymentAnalysis{
				Status:        AnalysisStatusUnpayable,
				FeeTotalInSat: 84,
				TotalInSat:    99056,
			},
		},
	}

	for _, tC := range testCases {
		t.Run(tC.desc, func(t *testing.T) {

			nts := defaultNTS
			if tC.nts != nil {
				nts = tC.nts
			}

			feeWindow := defaultFeeWindow
			if tC.feeWindow != nil {
				feeWindow = tC.feeWindow
			}

			analyzer := NewPaymentAnalyzer(feeWindow, nts, tC.feeBump)
			analysis, err := analyzer.ToInvoice(tC.payment)

			if err == nil && tC.err {
				t.Fatal("expected analysis to error")
			}
			if err != nil && !tC.err {
				t.Fatal(err)
			}
			if !reflect.DeepEqual(analysis, tC.expected) {
				t.Fatalf(
					"analysis does not match expected\n analysis: got %+v, expected %+v\nswapfees: got %+v, expected %+v",
					analysis,
					tC.expected,
					analysis.SwapFees,
					tC.expected.SwapFees,
				)
			}
		})
	}
}
