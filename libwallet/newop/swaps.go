package newop

import (
	"github.com/btcsuite/btcutil"
	"github.com/muun/libwallet/fees"
)

type SubmarineSwapReceiver struct {
	Alias            string
	NetworkAddresses string
	PublicKey        string
}

const (
	DebtTypeNone    = string(fees.DebtTypeNone)
	DebtTypeCollect = string(fees.DebtTypeCollect)
	DebtTypeLend    = string(fees.DebtTypeLend)
)

type SwapFees struct {
	RoutingFeeInSat     int64
	DebtType            string
	DebtAmountInSat     int64
	OutputAmountInSat   int64
	OutputPaddingInSat  int64
	ConfirmationsNeeded int64
}

func (f *SwapFees) toInternalType() *fees.SwapFees {
	if f == nil {
		return nil
	}
	return &fees.SwapFees{
		RoutingFee:          btcutil.Amount(f.RoutingFeeInSat),
		DebtType:            fees.DebtType(f.DebtType),
		DebtAmount:          btcutil.Amount(f.DebtAmountInSat),
		OutputAmount:        btcutil.Amount(f.OutputAmountInSat),
		OutputPadding:       btcutil.Amount(f.OutputPaddingInSat),
		ConfirmationsNeeded: uint(f.ConfirmationsNeeded),
	}
}

func newSwapFeesFromInternal(fees *fees.SwapFees) *SwapFees {
	return &SwapFees{
		RoutingFeeInSat:     int64(fees.RoutingFee),
		DebtType:            string(fees.DebtType),
		DebtAmountInSat:     int64(fees.DebtAmount),
		OutputAmountInSat:   int64(fees.OutputAmount),
		OutputPaddingInSat:  int64(fees.OutputPadding),
		ConfirmationsNeeded: int64(fees.ConfirmationsNeeded),
	}
}

type BestRouteFees struct {
	MaxCapacity              int64
	FeeProportionalMillionth int64
	FeeBase                  int64
}

func (f *BestRouteFees) toInternalType() *fees.BestRouteFees {
	if f == nil {
		return nil
	}
	return &fees.BestRouteFees{
		MaxCapacity:              btcutil.Amount(f.MaxCapacity),
		FeeProportionalMillionth: uint64(f.FeeProportionalMillionth),
		FeeBase:                  btcutil.Amount(f.FeeBase),
	}
}

type FundingOutputPolicies struct {
	MaximumDebtInSat       int64
	PotentialCollectInSat  int64
	MaxAmountInSatFor0Conf int64
}

func (f *FundingOutputPolicies) toInternalType() *fees.FundingOutputPolicies {
	if f == nil {
		return nil
	}
	return &fees.FundingOutputPolicies{
		MaximumDebt:       btcutil.Amount(f.MaximumDebtInSat),
		PotentialCollect:  btcutil.Amount(f.PotentialCollectInSat),
		MaxAmountFor0Conf: btcutil.Amount(f.MaxAmountInSatFor0Conf),
	}
}

type SubmarineSwap struct {
	Receiver              *SubmarineSwapReceiver
	Fees                  *SwapFees
	FundingOutputPolicies *FundingOutputPolicies
	BestRouteFees         []*BestRouteFees
}

func (s *SubmarineSwap) AddBestRouteFees(bestRouteFees *BestRouteFees) {
	s.BestRouteFees = append(s.BestRouteFees, bestRouteFees)
}

func (s *SubmarineSwap) toBestRouteFeesInternalType() []fees.BestRouteFees {
	var l []fees.BestRouteFees
	for _, bestRouteFee := range s.BestRouteFees {
		l = append(l, *(bestRouteFee.toInternalType()))
	}
	return l
}
