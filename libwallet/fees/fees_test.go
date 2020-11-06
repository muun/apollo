package fees

import (
	"reflect"
	"testing"

	"github.com/btcsuite/btcutil"
)

func TestComputeSwapFees(t *testing.T) {
	testCases := []struct {
		desc          string
		amount        btcutil.Amount
		bestRouteFees []BestRouteFees
		policies      *FundingOutputPolicies
		expected      *SwapFees
	}{
		{
			desc:   "smoke test",
			amount: 1000,
			bestRouteFees: []BestRouteFees{
				{
					MaxCapacity:              100000,
					FeeProportionalMillionth: 1,
					FeeBase:                  10,
				},
			},
			policies: &FundingOutputPolicies{
				MaximumDebt:       0,
				PotentialCollect:  0,
				MaxAmountFor0Conf: 0,
			},
			expected: &SwapFees{
				RoutingFee:          10,
				SweepFee:            0,
				DebtType:            DebtTypeNone,
				DebtAmount:          0,
				ConfirmationsNeeded: 1,
			},
		},
		{
			desc:   "qualifies for 0-conf",
			amount: 1000,
			bestRouteFees: []BestRouteFees{
				{
					MaxCapacity:              100000,
					FeeProportionalMillionth: 1,
					FeeBase:                  10,
				},
			},
			policies: &FundingOutputPolicies{
				MaximumDebt:       0,
				PotentialCollect:  0,
				MaxAmountFor0Conf: 1000000,
			},
			expected: &SwapFees{
				RoutingFee:          10,
				SweepFee:            0,
				DebtType:            DebtTypeNone,
				DebtAmount:          0,
				ConfirmationsNeeded: 0,
			},
		},
		{
			desc:   "qualifies for debt lend",
			amount: 1000,
			bestRouteFees: []BestRouteFees{
				{
					MaxCapacity:              100000,
					FeeProportionalMillionth: 1,
					FeeBase:                  10,
				},
			},
			policies: &FundingOutputPolicies{
				MaximumDebt:       1000000,
				PotentialCollect:  0,
				MaxAmountFor0Conf: 1000000,
			},
			expected: &SwapFees{
				RoutingFee:          10,
				SweepFee:            0,
				DebtType:            DebtTypeLend,
				DebtAmount:          1010,
				ConfirmationsNeeded: 0,
			},
		},
		{
			desc:   "debt collect",
			amount: 1000,
			bestRouteFees: []BestRouteFees{
				{
					MaxCapacity:              100000,
					FeeProportionalMillionth: 1,
					FeeBase:                  10,
				},
			},
			policies: &FundingOutputPolicies{
				MaximumDebt:       100,
				PotentialCollect:  1010,
				MaxAmountFor0Conf: 1000000,
			},
			expected: &SwapFees{
				RoutingFee:          10,
				SweepFee:            0,
				DebtType:            DebtTypeCollect,
				DebtAmount:          1010,
				ConfirmationsNeeded: 0,
			},
		},
		{
			desc:   "dust threshold",
			amount: 50,
			bestRouteFees: []BestRouteFees{
				{
					MaxCapacity:              100000,
					FeeProportionalMillionth: 1,
					FeeBase:                  10,
				},
			},
			policies: &FundingOutputPolicies{
				MaximumDebt:       0,
				PotentialCollect:  0,
				MaxAmountFor0Conf: 0,
			},
			expected: &SwapFees{
				RoutingFee:          10,
				SweepFee:            486,
				DebtType:            DebtTypeNone,
				DebtAmount:          0,
				ConfirmationsNeeded: 1,
			},
		},
		{
			desc:   "uses last route if route with enough capacity",
			amount: 1000,
			bestRouteFees: []BestRouteFees{
				{
					MaxCapacity:              900,
					FeeProportionalMillionth: 1,
					FeeBase:                  10,
				},
				{
					MaxCapacity:              900,
					FeeProportionalMillionth: 1,
					FeeBase:                  20,
				},
			},
			policies: &FundingOutputPolicies{
				MaximumDebt:       0,
				PotentialCollect:  0,
				MaxAmountFor0Conf: 0,
			},
			expected: &SwapFees{
				RoutingFee:          20,
				SweepFee:            0,
				DebtType:            DebtTypeNone,
				DebtAmount:          0,
				ConfirmationsNeeded: 1,
			},
		},
	}
	for _, tC := range testCases {
		t.Run(tC.desc, func(t *testing.T) {
			fees := ComputeSwapFees(tC.amount, tC.bestRouteFees, tC.policies)
			if !reflect.DeepEqual(fees, tC.expected) {
				t.Errorf("fees do not equal expected fees (%+v != %+v)", fees, tC.expected)
			}
		})
	}
}
