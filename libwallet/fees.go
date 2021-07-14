package libwallet

import (
	"github.com/btcsuite/btcutil"
	"github.com/muun/libwallet/fees"
)

type BestRouteFees struct {
	MaxCapacity              int64
	FeeProportionalMillionth int64
	FeeBase                  int64
}

type BestRouteFeesList struct {
	list []fees.BestRouteFees
}

func (l *BestRouteFeesList) Add(f *BestRouteFees) {
	l.list = append(l.list, fees.BestRouteFees{
		MaxCapacity:              btcutil.Amount(f.MaxCapacity),
		FeeProportionalMillionth: uint64(f.FeeProportionalMillionth),
		FeeBase:                  btcutil.Amount(f.FeeBase),
	})
}

type FundingOutputPolicies struct {
	MaximumDebt       int64
	PotentialCollect  int64
	MaxAmountFor0Conf int64
}

type SwapFees struct {
	RoutingFee          int64
	SweepFee            int64 // TODO: this should be called outputPadding, keeping name for retrocompat for now
	DebtType            string
	DebtAmount          int64
	ConfirmationsNeeded int64
}

func ComputeSwapFees(amount int64, bestRouteFees *BestRouteFeesList, policies *FundingOutputPolicies) *SwapFees {
	swapFees := fees.ComputeSwapFees(
		btcutil.Amount(amount),
		bestRouteFees.list,
		&fees.FundingOutputPolicies{
			MaximumDebt:       btcutil.Amount(policies.MaximumDebt),
			PotentialCollect:  btcutil.Amount(policies.PotentialCollect),
			MaxAmountFor0Conf: btcutil.Amount(policies.MaxAmountFor0Conf),
		},
		false,
	)
	return &SwapFees{
		RoutingFee:          int64(swapFees.RoutingFee),
		SweepFee:            int64(swapFees.OutputPadding),
		DebtType:            string(swapFees.DebtType),
		DebtAmount:          int64(swapFees.DebtAmount),
		ConfirmationsNeeded: int64(swapFees.ConfirmationsNeeded),
	}
}
