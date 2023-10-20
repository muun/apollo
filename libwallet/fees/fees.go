package fees

import "github.com/btcsuite/btcutil"

const dustThreshold = 546

// BestRouteFees represents a possible route for a lightning payment. In particular, it encodes the fee
// policy of such route (e.g how the route charges fees) and how a big a payment it can handle/route (e.g what is
// the maximum amount that is routable/payable via this route).
type BestRouteFees struct {
	MaxCapacity              btcutil.Amount // maximum amount that is routable/payable via this route
	FeeProportionalMillionth uint64         // fee proportion of the routed amount, divided by a million
	FeeBase                  btcutil.Amount // fixed fee component. For a specific route: TotalFee=(FeeProportionalMillionth*amount)/1000000 + FeeBase
}

// FundingOutputPolicies represents the conditions that decide how the funding output is created.
type FundingOutputPolicies struct {
	MaximumDebt       btcutil.Amount // maximum amount of debt that we're ok with lending this user, according swap provider risk tolerance
	PotentialCollect  btcutil.Amount // amount of debt we can effectively collect for a specific swap.
	MaxAmountFor0Conf btcutil.Amount // maximum amount allowed for a 0-conf swap. Greater amounts will require 1-conf (higher fees, worse UX). Depends on swap provider risk tolerance.
}

type DebtType string

const (
	DebtTypeNone    DebtType = "NONE"
	DebtTypeCollect DebtType = "COLLECT"
	DebtTypeLend    DebtType = "LEND"
)

type SwapFees struct {
	RoutingFee          btcutil.Amount
	DebtType            DebtType
	DebtAmount          btcutil.Amount
	OutputAmount        btcutil.Amount
	OutputPadding       btcutil.Amount
	ConfirmationsNeeded uint
}

func (p *FundingOutputPolicies) FundingConfirmations(paymentAmount, lightningFee btcutil.Amount) uint {
	totalAmount := paymentAmount + lightningFee
	if totalAmount <= p.MaxAmountFor0Conf {
		return 0
	}
	return 1
}

func (p *FundingOutputPolicies) DebtType(paymentAmount, lightningFee btcutil.Amount) DebtType {
	numConfirmations := p.FundingConfirmations(paymentAmount, lightningFee)
	totalAmount := paymentAmount + lightningFee
	if numConfirmations == 0 && totalAmount <= p.MaximumDebt {
		return DebtTypeLend
	}
	if p.PotentialCollect > 0 {
		return DebtTypeCollect
	}
	return DebtTypeNone
}

func (p *FundingOutputPolicies) DebtAmount(paymentAmount, lightningFee btcutil.Amount) btcutil.Amount {
	switch p.DebtType(paymentAmount, lightningFee) {
	case DebtTypeLend:
		return paymentAmount + lightningFee
	case DebtTypeCollect:
		return p.PotentialCollect
	case DebtTypeNone:
		return 0
	default:
		return 0
	}
}

func (p *FundingOutputPolicies) MinFundingAmount(paymentAmount, lightningFee btcutil.Amount) btcutil.Amount {
	inputAmount := paymentAmount + lightningFee
	if p.DebtType(paymentAmount, lightningFee) == DebtTypeCollect {
		inputAmount += p.DebtAmount(paymentAmount, lightningFee)
	}
	return inputAmount
}

func (p *FundingOutputPolicies) FundingOutputAmount(paymentAmount, lightningFee btcutil.Amount) btcutil.Amount {
	minAmount := p.MinFundingAmount(paymentAmount, lightningFee)
	if minAmount < dustThreshold {
		return dustThreshold
	}
	return minAmount
}

func (p *FundingOutputPolicies) FundingOutputPadding(paymentAmount, lightningFee btcutil.Amount) btcutil.Amount {
	minAmount := p.MinFundingAmount(paymentAmount, lightningFee)
	outputAmount := p.FundingOutputAmount(paymentAmount, lightningFee)
	return outputAmount - minAmount
}

func ComputeSwapFees(amount btcutil.Amount, bestRouteFees []BestRouteFees, policies *FundingOutputPolicies, takeFeeFromAmount bool) *SwapFees {
	if takeFeeFromAmount {
		// Handle edge cases for TFFA swaps. We don't allow lend for TFFA. This impacts sub-dust
		// swaps because we don't allow debt for output padding. Except, the very special case of
		// sub-dust TFFA swaps, in which you cant have output padding > 0 since you are using all
		// your balance and all your balance is < dust. In this case, since we can't use debt nor
		// output padding, if its necessary, the payment is unpayable.
		policies = &FundingOutputPolicies{
			MaximumDebt:       0,
			PotentialCollect:  policies.PotentialCollect,
			MaxAmountFor0Conf: policies.MaxAmountFor0Conf,
		}
	}

	lightningFee := computeLightningFee(amount, bestRouteFees)
	outputPadding := policies.FundingOutputPadding(amount, lightningFee)

	offchainFee := lightningFee + outputPadding
	outputAmount := amount + offchainFee

	debtType := policies.DebtType(amount, lightningFee)
	debtAmount := policies.DebtAmount(amount, lightningFee)
	if debtType == DebtTypeCollect {
		outputAmount += debtAmount
	} else if debtType == DebtTypeLend {
		outputAmount = 0
	}

	return &SwapFees{
		RoutingFee:          lightningFee,
		OutputPadding:       outputPadding,
		DebtType:            debtType,
		DebtAmount:          debtAmount,
		ConfirmationsNeeded: policies.FundingConfirmations(amount, lightningFee),
		OutputAmount:        outputAmount,
	}
}

func computeLightningFee(amount btcutil.Amount, bestRouteFees []BestRouteFees) btcutil.Amount {
	for _, fee := range bestRouteFees {
		if amount <= fee.MaxCapacity {
			return fee.ForAmount(amount)
		}
	}
	lastRouteFee := bestRouteFees[len(bestRouteFees)-1]
	return lastRouteFee.ForAmount(amount)
}

func (f *BestRouteFees) ForAmount(amount btcutil.Amount) btcutil.Amount {
	return (btcutil.Amount(f.FeeProportionalMillionth)*amount)/1000000 + f.FeeBase
}
