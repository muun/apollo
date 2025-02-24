package operation

import (
	"errors"
	"fmt"
	"strings"

	"github.com/btcsuite/btcd/btcutil"
	"github.com/muun/libwallet/fees"
)

// paymentAnalyzer is the component that decides whether a payment can be made or not, and what
// are the amount and fees involved. Here a list of some important design decisions:
//
// - Delegate to the caller the need to make another call if she wants to analyze with minimumFee
// - 2 entry points: toAddress for onchain payments, toInvoice for offchain payments
// - PaymentAnalyzer return errors only on programmer errors, not for "payment errors" aka
//   situations that can happen like amount greater than balance
// - Returned PaymentAnalysis contains a status that represent:
// 		- Success → `StatusOk`
//	    - Unrecoverable error -> `StatusAmountGreaterThanBalance`, `StatusAmountTooSmall`
//	    - Maybe recoverable error → `StatusUnpayable`
//	    - A status error is:
//	    	- Unrecoverable: if there's no way to pay the specified amount with the current state
//        	- Recoverable: if there's a way to pay the specified amount with the current state
//			(e.g changing the fee, if it applies)
// - `OutputAmount > uxtoBalance` IS NOT A `StatusAmountGreaterThanBalance`, because the problem
// 	is not that the `amount > totalBalance` but that the swapFees (sweep + lightning) and/or
// 	collectAmount make `OutputAmount > uxtoBalance`. Hence it is an `StatusUnpayable`.
// - We don't allow TFFA for LEND swaps (don't want to lend money if you're taking it all away)
// - FeeCalculator DOES NOT return error when amount > balance
// 		- Instead we return the fee it would take to spend all utxos and delegate to the caller the
//		 task of checking if that is spendable with the given amount
//    	- This is to avoid using go error handling
//	- We finally renamed sweepFee to outputPadding since that's its only use. Here's how it works:
//    	- Only makes sense for swaps
//    	- If amount ≥ DUST ⇒ No need for padding (outputPadding = 0)
//    	- If amount < DUST & debt < maxDebt ⇒ We use debt as padding (outputPadding = 0)
//    	- If amount < DUST && debt >=  maxDebt ⇒ outputPadding = DUST - amount
//    	- If amount < DUST and TFFA
//        	- Can't use debt as padding (don't lend money for TFFA)
//        	- Can't use fee as we are using all funds and they are < DUST
//        	- If there's need for padding ⇒ payment is UNPAYABLE
//        	- Hence, outputPadding = 0 or payment is UNPAYABLE

const DustThreshold = 546

type PaymentToAddress struct {
	TakeFeeFromAmount     bool
	AmountInSat           int64
	FeeRateInSatsPerVByte float64
}

type PaymentToInvoice struct {
	TakeFeeFromAmount     bool
	AmountInSat           int64
	SwapFees              *fees.SwapFees              // Nullable before we know the paymentAmount for amountless invoice
	BestRouteFees         []fees.BestRouteFees        // Nullable when we know the amount beforehand (invoice with amount)
	FundingOutputPolicies *fees.FundingOutputPolicies // Nullable when we know the amount beforehand (invoice with amount)
}

type PaymentAnalyzer struct {
	feeWindow           *FeeWindow
	nextTransactionSize *NextTransactionSize
	feeCalculator       *feeCalculator
}

type UtxoStatus string

const (
	UtxosStatusConfirmed   UtxoStatus = "CONFIRMED"
	UtxosStatusUnconfirmed UtxoStatus = "UNCONFIRMED"
)

var (
	utxoStatusMap = map[string]UtxoStatus{
		"confirmed":   UtxosStatusConfirmed,
		"unconfirmed": UtxosStatusUnconfirmed,
	}
)

func MapUtxoStatus(str string) (UtxoStatus, bool) {
	val, ok := utxoStatusMap[strings.ToLower(str)]
	return val, ok
}

type SizeForAmount struct {
	AmountInSat int64
	SizeInVByte int64
	Outpoint    string
	UtxoStatus  UtxoStatus
}

type NextTransactionSize struct {
	SizeProgression   []SizeForAmount
	ExpectedDebtInSat int64
}

func (nts *NextTransactionSize) UtxoBalance() int64 {
	if len(nts.SizeProgression) == 0 {
		return 0
	}
	return nts.SizeProgression[len(nts.SizeProgression)-1].AmountInSat
}

func (nts *NextTransactionSize) TotalBalance() int64 {
	return nts.UtxoBalance() - nts.ExpectedDebtInSat
}

type AnalysisStatus string

const (
	AnalysisStatusOk                       AnalysisStatus = "Ok"
	AnalysisStatusAmountGreaterThanBalance AnalysisStatus = "GreaterThanBalance"
	AnalysisStatusAmountTooSmall           AnalysisStatus = "AmountTooSmall"
	AnalysisStatusUnpayable                AnalysisStatus = "Unpayable"
)

// PaymentAnalysis encodes whether a payment can be made or not and some important extra metadata about the payment.
type PaymentAnalysis struct {
	Status        AnalysisStatus // encodes the result of a payment's analysis
	AmountInSat   int64          // payment amount (e.g the amount the recipient will receive)
	FeeTotalInSat int64          // encodes the onchain total fee (other fees may apply, e.g routing/lightning fee)
	FeeBumpInSat  int64          // fee bump to apply CPFP in unconfirmed utxos
	SwapFees      *fees.SwapFees // metadata related to the swap (if one exists for payment)
	TotalInSat    int64          // AmountInSat + fees (may include other than FeeTotalInSat). May provide extra information in case of error status (e.g payment can't be made).
}

func NewPaymentAnalyzer(
	feeWindow *FeeWindow,
	nts *NextTransactionSize,
	feeBumpFunctions []*FeeBumpFunction,
) *PaymentAnalyzer {
	return &PaymentAnalyzer{
		feeWindow:           feeWindow,
		nextTransactionSize: nts,
		feeCalculator:       &feeCalculator{nts, feeBumpFunctions},
	}
}

func (a *PaymentAnalyzer) totalBalance() int64 {
	return a.nextTransactionSize.TotalBalance()
}

func (a *PaymentAnalyzer) utxoBalance() int64 {
	return a.nextTransactionSize.UtxoBalance()
}

func (a *PaymentAnalyzer) ToAddress(payment *PaymentToAddress) (*PaymentAnalysis, error) {
	if payment.AmountInSat < DustThreshold {
		return &PaymentAnalysis{
			Status: AnalysisStatusAmountTooSmall,
		}, nil
	}
	if payment.AmountInSat > a.totalBalance() {
		return &PaymentAnalysis{
			Status:     AnalysisStatusAmountGreaterThanBalance,
			TotalInSat: payment.AmountInSat,
		}, nil
	}
	if payment.TakeFeeFromAmount && payment.AmountInSat != a.totalBalance() {
		return nil, fmt.Errorf("amount (%v) != userBalance (%v) for TFFA", payment.AmountInSat, a.totalBalance())
	}

	if payment.TakeFeeFromAmount {
		return a.analyzeFeeFromAmount(payment)
	}
	return a.analyzeFeeFromRemainingBalance(payment)
}

func (a *PaymentAnalyzer) analyzeFeeFromAmount(payment *PaymentToAddress) (*PaymentAnalysis, error) {
	fee, feeBump := a.feeCalculator.Fee(payment.AmountInSat, payment.FeeRateInSatsPerVByte, true)

	total := payment.AmountInSat
	amount := total - fee

	if amount <= DustThreshold {
		// avoid returning a negative amount
		if amount < 0 {
			amount = 0
		}
		return &PaymentAnalysis{
			Status:        AnalysisStatusUnpayable,
			AmountInSat:   amount,
			TotalInSat:    payment.AmountInSat,
			FeeTotalInSat: fee,
			FeeBumpInSat:  feeBump,
		}, nil
	}

	return &PaymentAnalysis{
		Status:        AnalysisStatusOk,
		AmountInSat:   amount,
		TotalInSat:    payment.AmountInSat,
		FeeTotalInSat: fee,
		FeeBumpInSat:  feeBump,
	}, nil
}

func (a *PaymentAnalyzer) analyzeFeeFromRemainingBalance(payment *PaymentToAddress) (*PaymentAnalysis, error) {
	fee, feeBump := a.feeCalculator.Fee(payment.AmountInSat, payment.FeeRateInSatsPerVByte, false)
	total := payment.AmountInSat + fee

	if total > a.totalBalance() {
		return &PaymentAnalysis{
			Status:        AnalysisStatusUnpayable,
			AmountInSat:   payment.AmountInSat,
			FeeTotalInSat: fee,
			FeeBumpInSat:  feeBump,
			TotalInSat:    total,
		}, nil
	}

	return &PaymentAnalysis{
		Status:        AnalysisStatusOk,
		AmountInSat:   payment.AmountInSat,
		TotalInSat:    total,
		FeeTotalInSat: fee,
		FeeBumpInSat:  feeBump,
	}, nil
}

func (a *PaymentAnalyzer) ToInvoice(payment *PaymentToInvoice) (*PaymentAnalysis, error) {
	if payment.AmountInSat <= 0 {
		return &PaymentAnalysis{
			Status: AnalysisStatusAmountTooSmall,
		}, nil
	}
	if payment.AmountInSat > a.totalBalance() {
		return &PaymentAnalysis{
			Status:     AnalysisStatusAmountGreaterThanBalance,
			TotalInSat: payment.AmountInSat,
		}, nil
	}
	if payment.TakeFeeFromAmount {
		if payment.BestRouteFees == nil {
			return nil, errors.New("fixed amount swap can't be TFFA since that would change the amount")
		}
		if payment.AmountInSat != a.totalBalance() {
			return nil, fmt.Errorf("amount (%v) != userBalance (%v) for TFFA", payment.AmountInSat, a.totalBalance())
		}
	}

	if payment.BestRouteFees != nil {

		// As users can enter newOp screen with 0 balance, we need to check for amount == 0
		// because of our rule (if balance == amount then useAllFunds = true)
		if !payment.TakeFeeFromAmount || payment.AmountInSat == 0 {
			// User chose a specific amount
			return a.analyzeFixedAmountSwap(payment, fees.ComputeSwapFees(
				btcutil.Amount(payment.AmountInSat),
				payment.BestRouteFees,
				payment.FundingOutputPolicies,
				payment.TakeFeeFromAmount,
			))

		} else {
			return a.analyzeTFFAAmountlessInvoiceSwap(payment)
		}
	}

	if payment.SwapFees == nil {
		return nil, fmt.Errorf("payment is missing required swap fees data")
	}

	return a.analyzeFixedAmountSwap(payment, payment.SwapFees)
}

func (a *PaymentAnalyzer) analyzeFixedAmountSwap(payment *PaymentToInvoice, swapFees *fees.SwapFees) (*PaymentAnalysis, error) {
	switch swapFees.DebtType {
	case fees.DebtTypeLend:
		return a.analyzeLendSwap(payment, swapFees)
	case fees.DebtTypeCollect:
		fallthrough
	case fees.DebtTypeNone:
		return a.analyzeCollectSwap(payment, swapFees) // a non-debt swap is just a collect swap with debtAmount = 0
	}
	return nil, fmt.Errorf("unsupported debt type: %v", swapFees.DebtType)
}

func (a *PaymentAnalyzer) analyzeLendSwap(payment *PaymentToInvoice, swapFees *fees.SwapFees) (*PaymentAnalysis, error) {

	amount := payment.AmountInSat
	total := amount + int64(swapFees.RoutingFee)

	if total > a.totalBalance() {
		return &PaymentAnalysis{
			Status:        AnalysisStatusUnpayable,
			AmountInSat:   amount,
			TotalInSat:    total,
			FeeTotalInSat: 0,
			SwapFees:      swapFees,
		}, nil
	}

	return &PaymentAnalysis{
		Status:        AnalysisStatusOk,
		AmountInSat:   amount,
		TotalInSat:    total,
		FeeTotalInSat: 0,
		SwapFees:      swapFees,
	}, nil
}

// Analyze non LEND swaps (e.g both COLLECT and NON-DEBT swaps), understanding that both cases warrant
// the same analysis. A non-debt swap is just a collect swap with debtAmount = 0.
func (a *PaymentAnalyzer) analyzeCollectSwap(payment *PaymentToInvoice, swapFees *fees.SwapFees) (*PaymentAnalysis, error) {

	outputAmount := int64(swapFees.OutputAmount)
	collectAmount := int64(swapFees.DebtAmount)

	expectedOutputAmount := payment.AmountInSat +
		int64(swapFees.RoutingFee) +
		int64(swapFees.OutputPadding) +
		collectAmount

	if outputAmount != expectedOutputAmount {
		return nil, fmt.Errorf(
			"swap integrity check failed (outputAmount=%v, original_amount=%v, routing_fee=%v, output_padding=%v, collect_amount=%v)",
			outputAmount,
			payment.AmountInSat,
			int64(swapFees.RoutingFee),
			int64(swapFees.OutputPadding),
			collectAmount,
		)
	}

	feeRate, err := a.feeWindow.SwapFeeRate(swapFees.ConfirmationsNeeded)

	if err != nil {
		return nil, err
	}

	fee, feeBump := a.feeCalculator.Fee(outputAmount, feeRate, false)
	total := outputAmount + fee
	totalForUser := total - collectAmount

	if total > a.utxoBalance() || totalForUser > a.totalBalance() {
		return &PaymentAnalysis{
			Status:        AnalysisStatusUnpayable,
			AmountInSat:   payment.AmountInSat,
			FeeTotalInSat: fee,
			FeeBumpInSat:  feeBump,
			TotalInSat:    totalForUser,
			SwapFees:      swapFees,
		}, nil
	}

	return &PaymentAnalysis{
		Status:        AnalysisStatusOk,
		AmountInSat:   payment.AmountInSat,
		FeeTotalInSat: fee,
		FeeBumpInSat:  feeBump,
		TotalInSat:    totalForUser,
		SwapFees:      swapFees,
	}, nil
}

// analyzeTFFAAmountlessInvoiceSwap takes care of the insurmountable task of deciding whether a take
// take fee from amount payment for an amountless invoice swap can be made, and (if it can) what are
// the fees and the destination/payment amount.
// This is particularly tricky since we have kind of a circular dependency: we don't know the payment amount which
// determines the fees (on-chain and lightning), and we need both to determine the number of confirmations required for
// the swap, which affects the on-chain fee, which affects the amount (since this is TFFA).
// For this implementation built from the assumptions that 0-conf on-chain fees are lower than 1-conf fees
// (since we don't have to wait for a block to make the payment).
// Here we go:
// 1. We calculate the on-chain fee for a 0-conf swap spending all funds
//   - If that fee is greater than our balance -> payment can't be made (VERY low balance scenario)
//
// 2. We calculate the amount, routing fee and on-chain fee for a 0-conf TFFA swap
// 3. We determine the number of confirmations required for the calculated amount and routing fee.
//   - If 0-conf -> we're good to continue
//   - If 1-conf -> we perform step 2 for a 1-conf TFF swap (re calculate amount, routing fee and on-chain fee)
//
// 4. If amount <= 0 -> payment can't be made
// If amount > 0 -> AWESOME! That's the payment amount.
// 5. We determine the params of the funding output (SwapFees) and perform final checks to decide if payment can be made
func (a *PaymentAnalyzer) analyzeTFFAAmountlessInvoiceSwap(payment *PaymentToInvoice) (*PaymentAnalysis, error) {
	zeroConfFeeRate, err := a.feeWindow.SwapFeeRate(0)

	if err != nil {
		return nil, err
	}

	zeroConfFeeInSat, zeroConfFeeBumpInSat := a.computeFeeForTFFASwap(payment, zeroConfFeeRate)
	if zeroConfFeeInSat > a.totalBalance() {
		// We can't even pay the onchain fee
		return &PaymentAnalysis{
			Status:        AnalysisStatusUnpayable,
			FeeTotalInSat: zeroConfFeeInSat,
			FeeBumpInSat:  zeroConfFeeBumpInSat,
			TotalInSat:    a.totalBalance() + int64(zeroConfFeeRate),
		}, nil
	}

	params, err := a.computeParamsForTFFASwap(payment, 0)
	if err != nil {
		// This LITERALLY can never happen, as only source of error for computeParamsForTFFASwap are:
		//  - negative conf target (we're using 0)
		//  - no route for amount (should be guaranteed by BestRouteFees struct)
		return &PaymentAnalysis{
			Status:        AnalysisStatusUnpayable,
			FeeTotalInSat: zeroConfFeeInSat,
			FeeBumpInSat:  zeroConfFeeBumpInSat,
			TotalInSat:    a.totalBalance() + zeroConfFeeInSat,
		}, nil
	}

	confirmations := payment.FundingOutputPolicies.FundingConfirmations(params.Amount, params.RoutingFee)
	if confirmations == 1 {
		params, err = a.computeParamsForTFFASwap(payment, 1)
		if err != nil {
			// This LITERALLY can never happen, as only source of error for computeParamsForTFFASwap are:
			//  - negative conf target (we're using 1)
			//  - no route for amount (should be guaranteed by BestRouteFees struct)
			return &PaymentAnalysis{
				Status:        AnalysisStatusUnpayable,
				FeeTotalInSat: zeroConfFeeInSat,
				FeeBumpInSat:  zeroConfFeeBumpInSat,
				TotalInSat:    a.totalBalance() + zeroConfFeeInSat,
			}, nil
		}
	}

	amount := params.Amount
	lightningFee := params.RoutingFee
	onChainFee := params.OnChainFee
	onChainFeeBump := params.OnChainFeeBump

	if amount <= 0 {
		// We can't pay the combined fee
		// This can be either cause we can't pay both fees summed or we had to bump to
		// 1-conf and we can't pay that.
		return &PaymentAnalysis{
			Status:        AnalysisStatusUnpayable,
			FeeTotalInSat: zeroConfFeeInSat,
			FeeBumpInSat:  zeroConfFeeBumpInSat,
			TotalInSat:    a.totalBalance() + zeroConfFeeInSat,
		}, nil
	}

	swapFees := fees.ComputeSwapFees(amount, payment.BestRouteFees, payment.FundingOutputPolicies, true)

	if swapFees.DebtType == fees.DebtTypeLend {
		return nil, errors.New("TFFA swap should not be a lend operation")
	}

	// This assumes that debt amount is either 0 (DebtType = NONE) or positive (DebtType = COLLECT)
	outputAmount := amount + lightningFee + swapFees.OutputPadding + swapFees.DebtAmount

	if lightningFee != swapFees.RoutingFee {
		return nil, fmt.Errorf(
			"integrity error: inconsistent lightning fee calculated for TFFA swap (lightning_fee=%v, output_amount=%v, original_amount=%v, routing_fee=%v, output_padding=%v)",
			int64(lightningFee),
			int64(outputAmount),
			payment.AmountInSat,
			int64(swapFees.RoutingFee),
			int64(swapFees.OutputPadding),
		)
	}

	total := outputAmount + onChainFee
	totalForDisplay := total - swapFees.DebtAmount // amount + lightningFee + outputPadding + onChainFee

	// We need to ensure we can spend on chain and that we have enough UI visible balance too
	// That is, the collect doesn't make us spend more than we really can and the amount + fee
	// doesn't default any debt.
	canPay := total <= btcutil.Amount(a.utxoBalance()) && totalForDisplay <= btcutil.Amount(a.totalBalance())

	if !canPay {
		return &PaymentAnalysis{
			Status:        AnalysisStatusUnpayable,
			AmountInSat:   int64(amount),
			FeeTotalInSat: int64(onChainFee),
			FeeBumpInSat:  int64(onChainFeeBump),
			TotalInSat:    payment.AmountInSat,
			SwapFees:      swapFees,
		}, nil
	}

	return &PaymentAnalysis{
		Status:        AnalysisStatusOk,
		AmountInSat:   int64(amount),
		FeeTotalInSat: int64(onChainFee),
		FeeBumpInSat:  int64(onChainFeeBump),
		TotalInSat:    payment.AmountInSat,
		SwapFees:      swapFees,
	}, nil
}

type swapParams struct {
	Amount         btcutil.Amount
	OnChainFee     btcutil.Amount
	OnChainFeeBump btcutil.Amount
	RoutingFee     btcutil.Amount
}

// computeParamsForTFFASwap takes care of the VERY COMPLEX task of calculating
// the amount, routing fee and on-chain fee for a TFFA swap, given a specified
// number of confirmations required for the swap. Calculating the on-chain fee
// is pretty straightforward but for the other two we use what we call
// "the equation". Let's dive into how it works:
//
// Let:
// - x be the payment amount
// - l be the lightning/routing fee
// - h be the onchain fee for amount x
// - y the available user balance (utxoBalance - expectedDebt)
//
// Also, given a specific route, l is a linear function of x l(x) = a * x + b,
// where a and b are known.
//
// For amountLessInvoices, we need to figure out x and l such that x + l = y - h
//
// Note that we don't care about debt (except to calculate user balance) since it
// doesn't affect that payment/offchain amount and thus the routingFee. It may
// affect the onchain fee though, which is already calculated considering it.
// We also don't care about output padding, since it can either be:
//   - issued debt, in which case point above still holds
//   - taken by fee, which would mean we are in a TFFA for a sub-dust amount,
//     which is unpayable since we don't have balance to add as padding/fee.
//
// Suppose we have only one route.
// Then, x + l(x) = y - h
// Then, x + (a * x + b) = y - h
// Then, x * (1 + a) = y - h - b
// Then, x = (y - h - b) / (a + 1)   (*1)
//
// BUT, we can have different routes for different amounts, aka:
// l_1(x) = a_1 * x + b_1
// l_2(x) = a_2 * x + b_2
// l_3(x) = a_3 * x + b_3
// etc...
//
// What we can do is try out each l(x) corresponding to each route and check
// which one gives us a valid amount (e.g amount < route.maxCapacityInSat), in
// other words, which route is the "best route" associated with that amount.
// BestRouteFees guarantees that for each amount there's only one "best route"
// and that there is a route for each amount.
//
// Final note, our final equation looks a little different since our param a
// is given as FeeProportionalMillionth/1_000_000, so in order to not lose
// precision we need to massage equation *1 bit more:
//
// x = (y - h - b) / (FeeProportionalMillionth/1_000_000 + 1)
// x = (y - h - b) / (FeeProportionalMillionth + 1_000_000) / 1_000_000
// x = ((y - h - b) * 1_000_000) / (FeeProportionalMillionth + 1_000_000)
func (a *PaymentAnalyzer) computeParamsForTFFASwap(payment *PaymentToInvoice, confs uint) (*swapParams, error) {
	feeRate, err := a.feeWindow.SwapFeeRate(confs)

	if err != nil {
		return nil, err
	}

	onChainFeeInSat, onChainFeeBumpInSat := a.computeFeeForTFFASwap(payment, feeRate)

	for _, bestRouteFees := range payment.BestRouteFees {
		amount := btcutil.Amount(
			(a.totalBalance() - onChainFeeInSat - int64(bestRouteFees.FeeBase)) * 1_000_000 /
				(int64(bestRouteFees.FeeProportionalMillionth) + 1_000_000))

		lightningFee := bestRouteFees.ForAmount(amount)

		// Warning, this is an ugly hack because we are not (yet) using millisats as unit of
		// account thus we might an amount 1 sat smaller than the previous implementation.
		// What this check does is to see if we can send just one sat more with the safe fee
		// in sats. It probably has some edge cases in some cases but we have been ignoring
		// them for now so we can live with it.
		if bestRouteFees.ForAmount(amount+1) == lightningFee {
			amount += 1
		}

		if amount+lightningFee <= bestRouteFees.MaxCapacity {

			// There's a special comment to be made here for VERY edgy case where
			// bestRouteFees.ForAmount(amount+1) == lightningFee+1.
			// In this case adding 1 sat to the amount makes you need an extra sat for the
			// routingFee, and these 2 extra sats make the total go over userBalance, but
			// there's 1 sat available in our balance. What do we do it? Answer: nothing,
			// it will be burn as on-chain fee.

			return &swapParams{
				Amount:         amount,
				RoutingFee:     lightningFee,
				OnChainFee:     btcutil.Amount(onChainFeeInSat),
				OnChainFeeBump: btcutil.Amount(onChainFeeBumpInSat),
			}, nil
		}
	}

	// This shouldn't happen. BestRouteFees should guarantee that there's a route for each amount.
	return nil, errors.New("none of the best route fees have enough capacity")
}

func (a *PaymentAnalyzer) computeFeeForTFFASwap(
	payment *PaymentToInvoice,
	feeRate float64,
) (totalFee int64, feeBump int64) {
	// Compute tha on-chain fee. As its TFFA, we want to calculate the fee for the total balance
	// including any sats we want to collect.
	onChainAmount := a.totalBalance() + int64(payment.FundingOutputPolicies.PotentialCollect)

	return a.feeCalculator.Fee(onChainAmount, feeRate, true)
}
