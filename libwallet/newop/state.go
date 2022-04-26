package newop

import (
	"fmt"
	"strings"
	"time"

	"github.com/btcsuite/btcutil"
	"github.com/muun/libwallet"
	"github.com/muun/libwallet/operation"
)

// Transitions that involve asynchronous work block, so apps should always fire in background and
// observe results.
//
// Each State has a concrete type, that embeds a BaseState. The BaseState holds data shared by
// all states, usually forwarded with every transition.

// State is an interface implemented by every state type, containing flow-level properties.
type State interface {
	GetUpdate() string
}

// States are emitted in Transitions and contain a certain Update type.
// UpdateAll is the default. Means the state is meant to update the UI entirely. This is currently
// handled differently by clients. Falcon takes it as a built UI from scratch, while Apollo
// only builds from scratch if it has to, otherwise it just updates values.
// UpdateEmpty means the State machine is coming back to a previous State and UI needs no further
// updating.
// UpdateInPlace is a special update type for Falcon, it's used to just update values in place,
// no building UI from scratch.
const (
	UpdateAll     = ""
	UpdateEmpty   = "UpdateEmpty"
	UpdateInPlace = "UpdateInPlace"
)

// TransitionListener allows app-level code to receive state updates asynchronously, preserving
// concrete types across the bridge and enforcing type safety.
type TransitionListener interface {
	OnStart(nextState *StartState)
	OnResolve(nextState *ResolveState)
	OnEnterAmount(nextState *EnterAmountState)
	OnEnterDescription(nextState *EnterDescriptionState)
	OnValidate(nextState *ValidateState)
	OnValidateLightning(nextState *ValidateLightningState)
	OnConfirm(nextState *ConfirmState)
	OnConfirmLightning(nextState *ConfirmLightningState)
	OnEditFee(nextState *EditFeeState)
	OnError(nextState *ErrorState)
	OnBalanceError(nextState *BalanceErrorState)
	OnAbort(nextState *AbortState)
}

// NewOperationFlow sets up the StartState, so transitions will be reported to `listener`
func NewOperationFlow(listener TransitionListener) *StartState {
	initial := &StartState{
		BaseState: BaseState{
			listener: listener,
		},
	}

	return initial
}

// -------------------------------------------------------------------------------------------------

// BaseState contains the shared structure among all states
type BaseState struct {
	listener TransitionListener
	update   string
}

func (s *BaseState) GetUpdate() string {
	return s.update
}

type Resolved struct {
	BaseState
	PaymentIntent  *PaymentIntent
	PaymentContext *PaymentContext
	PresetAmount   *BitcoinAmount
	PresetNote     string
}

func (r Resolved) emitError(error string) error {
	nextState := &ErrorState{
		BaseState:     r.BaseState,
		PaymentIntent: r.PaymentIntent,
		Error:         error,
	}
	nextState.emit()

	return nil
}

func (r Resolved) emitBalanceError(error string, analysis *operation.PaymentAnalysis, inputCurrency string) error {

	toMonetaryAmount := func(sats int64) *MonetaryAmount {
		window := r.PaymentContext.ExchangeRateWindow
		return window.convert(NewMonetaryAmountFromSatoshis(sats), inputCurrency)
	}

	next := &BalanceErrorState{
		BaseState:     r.BaseState,
		PaymentIntent: r.PaymentIntent,
		TotalAmount:   toMonetaryAmount(analysis.TotalInSat),
		Balance:       toMonetaryAmount(r.PaymentContext.totalBalance()),
		Error:         error,
	}
	next.emit()

	return nil
}

type AmountInfo struct {
	Amount                *BitcoinAmount
	TotalBalance          *BitcoinAmount
	TakeFeeFromAmount     bool
	FeeRateInSatsPerVByte float64
}

func (a *AmountInfo) mutating(f func(*AmountInfo)) *AmountInfo {
	// Deref to make a copy before mutating. Otherwise we mutate the original.
	mutated := *a
	f(&mutated)
	return &mutated
}

type Validated struct {
	analysis       *operation.PaymentAnalysis
	Fee            *BitcoinAmount
	FeeNeedsChange bool
	Total          *BitcoinAmount
	SwapInfo       *SwapInfo
}

type SwapInfo struct {
	IsOneConf  bool
	OnchainFee *BitcoinAmount
	SwapFees   *SwapFees
}

// -------------------------------------------------------------------------------------------------

type StartState struct {
	BaseState
}

func (s *StartState) Resolve(address string, network *libwallet.Network) error {
	uri, err := libwallet.GetPaymentURI(address, network)
	if err != nil {
		return err
	}

	if len(uri.Bip70Url) > 0 {
		// We resolve BIP70 here to avoid hoping about in the apps with the data
		// However, this has the consequence of making the BIP70 resolution
		// and loading of real time data sequential. While this is a bit worse
		// BIP70 is rarely used, and we should usually have RTD up to date.

		go s.resolveBip70(uri, network)
		return nil
	}

	next := &ResolveState{
		BaseState: s.BaseState,
		PaymentIntent: &PaymentIntent{
			URI: uri,
		},
	}

	next.emit()
	return nil
}

func (s *StartState) resolveBip70(uri *libwallet.MuunPaymentURI, network *libwallet.Network) {

	intent := &PaymentIntent{
		URI: uri,
	}

	bip70, err := libwallet.DoPaymentRequestCall(uri.Bip70Url, network)
	if err != nil {

		fmt.Printf("Error resolving bip70 uri: %s. Error: %v\n", uri.Bip70Url, err)

		// If we failed to resolve the URI but we had an address set, use that!
		if len(uri.Address) > 0 {
			next := &ResolveState{
				BaseState:     s.BaseState,
				PaymentIntent: intent,
			}
			next.emit()

			// If the error contains the expired string, that means that the invoice has expired
		} else if strings.Contains(err.Error(), "failed to unmarshal payment request") {
			next := &ErrorState{
				BaseState:     s.BaseState,
				PaymentIntent: intent,
				Error:         OperationErrorInvoiceExpired,
			}
			next.emit()

			// In any other case we display the invalid address message
		} else {
			next := &ErrorState{
				BaseState:     s.BaseState,
				PaymentIntent: intent,
				Error:         OperationErrorInvalidAddress,
			}
			next.emit()
		}

	} else {

		fmt.Printf("Successfully resolved bip70 uri: %s\n", uri.Bip70Url)
		next := &ResolveState{
			BaseState: s.BaseState,
			PaymentIntent: &PaymentIntent{
				URI: bip70,
			},
		}
		next.emit()
	}
}

func (s *StartState) ResolveInvoice(invoice *libwallet.Invoice, network *libwallet.Network) error {
	next := &ResolveState{
		BaseState: s.BaseState,
		PaymentIntent: &PaymentIntent{
			URI: &libwallet.MuunPaymentURI{
				Invoice: invoice,
			},
		},
	}

	next.emit()
	return nil
}

// -------------------------------------------------------------------------------------------------

// PaymentIntent contains the resolved payment intent and does not change during the flow
type PaymentIntent struct {
	URI *libwallet.MuunPaymentURI
}

func (p *PaymentIntent) Amount() *MonetaryAmount {
	if p.URI.Invoice != nil && p.URI.Invoice.Sats != 0 {
		return NewMonetaryAmountFromSatoshis(p.URI.Invoice.Sats)
	}
	if p.URI.Amount != "" {
		return NewMonetaryAmountFromFiat(p.URI.Amount, "BTC")
	}
	return nil
}

type ResolveState struct {
	BaseState
	PaymentIntent *PaymentIntent
}

func (s *ResolveState) SetContext(context *PaymentContext) error {
	return s.setContextWithTime(context, time.Now())
}

// setContextWithTime is meant only for testing, allows caller to use a fixed time to check invoice expiration
func (s *ResolveState) setContextWithTime(context *PaymentContext, now time.Time) error {

	// TODO(newop): add type to PaymentIntent to clarify lightning/onchain distinction
	invoice := s.PaymentIntent.URI.Invoice
	totalBalance := context.toBitcoinAmount(context.totalBalance(), context.PrimaryCurrency)

	if invoice != nil {
		// check expired and send to error state
		if time.Unix(invoice.Expiry, 0).Before(now) {
			return s.emitError(OperationErrorInvoiceExpired)
		}

		if invoice.Sats > 0 {
			return s.emitValidateLightning(context, invoice, totalBalance)
		}

		return s.emitAmount(context, totalBalance)
	}

	amount := s.PaymentIntent.Amount()

	if s.PaymentIntent.URI.Amount != "" && amount == nil {
		return s.emitError(OperationErrorInvalidAddress)
	}

	if amount != nil {

		if amount.toBtc(context.ExchangeRateWindow) < operation.DustThreshold {
			return s.emitError(OperationErrorAmountTooSmall)
		}

		amount := amount.toBitcoinAmount(context.ExchangeRateWindow, context.PrimaryCurrency)
		return s.emitValidate(&Resolved{
			BaseState:      s.BaseState,
			PaymentIntent:  s.PaymentIntent,
			PaymentContext: context,
			PresetAmount:   amount,
		}, amount, context)
	}

	return s.emitAmount(context, totalBalance)
}

func (s *ResolveState) emitAmount(context *PaymentContext, totalBalance *BitcoinAmount) error {

	amount := NewMonetaryAmountFromFiat("0", context.PrimaryCurrency).
		toBitcoinAmount(context.ExchangeRateWindow, context.PrimaryCurrency)

	nextState := &EnterAmountState{
		Resolved: &Resolved{
			BaseState:      s.BaseState,
			PaymentIntent:  s.PaymentIntent,
			PaymentContext: context,
		},
		Amount:       amount,
		TotalBalance: totalBalance,
	}
	nextState.emit()
	return nil
}

func (s *ResolveState) emitValidateLightning(context *PaymentContext, invoice *libwallet.Invoice, totalBalance *BitcoinAmount) error {
	presetAmount := context.toBitcoinAmount(invoice.Sats, "BTC")
	presetNote := s.PaymentIntent.URI.Invoice.Description
	nextState := &ValidateLightningState{
		Resolved: &Resolved{
			BaseState:      s.BaseState,
			PaymentIntent:  s.PaymentIntent,
			PaymentContext: context,
			PresetAmount:   presetAmount,
			PresetNote:     presetNote,
		},
		AmountInfo: &AmountInfo{
			Amount:            presetAmount,
			TotalBalance:      totalBalance,
			TakeFeeFromAmount: false,
		},
		Note: presetNote,
	}
	nextState.emit()

	return nil
}

func (s *ResolveState) emitValidate(resolved *Resolved, amount *BitcoinAmount, context *PaymentContext) error {

	nextState := &ValidateState{
		Resolved: resolved,
		AmountInfo: &AmountInfo{
			TotalBalance:          context.toBitcoinAmount(context.totalBalance(), "BTC"),
			Amount:                amount,
			TakeFeeFromAmount:     false,
			FeeRateInSatsPerVByte: context.FeeWindow.toInternalType().FastestFeeRate(),
		},
	}
	nextState.emit()

	return nil
}

func (s *ResolveState) emitError(error string) error {
	nextState := &ErrorState{
		BaseState:     s.BaseState,
		PaymentIntent: s.PaymentIntent,
		Error:         error,
	}
	nextState.emit()

	return nil
}

func (s *ResolveState) emit() {
	s.listener.OnResolve(s)
}

// -------------------------------------------------------------------------------------------------

type EnterAmountState struct {
	*Resolved
	Amount       *BitcoinAmount
	TotalBalance *BitcoinAmount
}

func (s *EnterAmountState) EnterAmount(amount *MonetaryAmount, takeFeeFromAmount bool) error {

	// Let's enforce this business logic here. Our payment analyzer enforces
	//      IF tffa/useAllFunds THEN amount == balance
	// But we also want the contraposition:
	//      IF amount == balance THEN tffa/useAllFunds
	if amount.String() == s.TotalBalance.InInputCurrency.String() {
		amount = s.TotalBalance.InInputCurrency
		takeFeeFromAmount = true
	}

	feeWindow := s.PaymentContext.FeeWindow.toInternalType()
	amountInfo := &AmountInfo{
		Amount: amount.toBitcoinAmount(
			s.PaymentContext.ExchangeRateWindow,
			s.PaymentContext.PrimaryCurrency,
		),
		TakeFeeFromAmount:     takeFeeFromAmount,
		TotalBalance:          s.TotalBalance,
		FeeRateInSatsPerVByte: feeWindow.FastestFeeRate(),
	}

	if s.Resolved.PaymentIntent.URI.Invoice != nil {

		nextState := &ValidateLightningState{
			Resolved:   s.Resolved,
			AmountInfo: amountInfo,
			Note:       s.PaymentIntent.URI.Invoice.Description,
		}
		nextState.emit()

	} else {

		nextState := &ValidateState{
			Resolved:   s.Resolved,
			AmountInfo: amountInfo,
			Note:       s.Resolved.PaymentIntent.URI.Message,
		}
		nextState.emit()
	}

	return nil
}

func (s *EnterAmountState) PartialValidate(inputAmount *MonetaryAmount) (bool, error) {

	amountInSat := int64(inputAmount.toBtc(s.PaymentContext.ExchangeRateWindow))
	isSwap := s.PaymentContext.SubmarineSwap != nil

	minAmount := int64(operation.DustThreshold)
	if isSwap {
		minAmount = 0
	}

	if amountInSat < minAmount || amountInSat > s.TotalBalance.InSat {
		return false, nil
	}

	return true, nil
}

// ChangeCurrency is deprecated. Prefer the newer, ChangeCurrencyWithAmount(currency, inputAmount)
func (s *EnterAmountState) ChangeCurrency(currency string) error {
	return s.ChangeCurrencyWithAmount(currency, s.Amount.InInputCurrency)
}

// ChangeCurrencyWithAmount respond to the user action of changing the current input currency to a new one,
// while also updating the input amount, needed for performing the necessary conversion.
// Note: this state machine doesn't receive partial updates for the input amount each time the
// user types or deletes a digit, so ChangeCurrencyWithAmount needs to receive the updates input amount.
func (s *EnterAmountState) ChangeCurrencyWithAmount(currency string, inputAmount *MonetaryAmount) error {
	exchangeRateWindow := s.PaymentContext.ExchangeRateWindow

	newTotalBalance := s.PaymentContext.toBitcoinAmount(
		s.PaymentContext.totalBalance(),
		currency,
	)

	var amount *BitcoinAmount
	// IF amount == balance THEN tffa/useAllFunds
	// See EnterAmount transition.
	if inputAmount.String() == s.TotalBalance.InInputCurrency.String() {
		amount = newTotalBalance
	} else {
		amount = &BitcoinAmount{
			InSat:             int64(inputAmount.toBtc(exchangeRateWindow)),
			InInputCurrency:   exchangeRateWindow.convert(inputAmount, currency),
			InPrimaryCurrency: exchangeRateWindow.convert(inputAmount, s.PaymentContext.PrimaryCurrency),
		}
	}

	nextState := &EnterAmountState{
		Resolved:     s.Resolved,
		Amount:       amount,
		TotalBalance: newTotalBalance,
	}
	nextState.emitUpdate(UpdateInPlace)

	return nil
}

func (s *EnterAmountState) Back() error {
	nextState := &AbortState{
		BaseState: BaseState{
			listener: s.listener, // create new BaseState to avoid passing along update string
		},
		PreviousState: s,
	}
	nextState.emit()

	return nil
}

func (s *EnterAmountState) emit() {
	s.emitUpdate(UpdateAll)
}

func (s *EnterAmountState) emitUpdate(update string) {
	s.update = update
	s.listener.OnEnterAmount(s)
}

// -------------------------------------------------------------------------------------------------

type EnterDescriptionState struct {
	*Resolved
	*AmountInfo
	*Validated
	Note string
}

func (s *EnterDescriptionState) EnterDescription(description string) error {

	if s.PaymentIntent.URI.Invoice == nil {

		nextState := &ConfirmState{
			Resolved:   s.Resolved,
			AmountInfo: s.AmountInfo,
			Validated:  s.Validated,
			Note:       description,
		}
		nextState.emit()

	} else {

		nextState := &ConfirmLightningState{
			Resolved:   s.Resolved,
			AmountInfo: s.AmountInfo,
			Validated:  s.Validated,
			Note:       description,
		}

		nextState.emit()
	}

	return nil
}

func (s *EnterDescriptionState) Back() error {

	if s.PaymentIntent.Amount() != nil {

		nextState := &AbortState{
			BaseState: BaseState{
				listener: s.listener, // create new BaseState to avoid passing along update string
			},
			PreviousState: s,
		}
		nextState.emit()

	} else {

		amount := s.Amount
		if s.TakeFeeFromAmount {
			amount = s.TotalBalance
		}

		nextState := &EnterAmountState{
			Resolved:     s.Resolved,
			Amount:       amount,
			TotalBalance: s.TotalBalance,
		}
		nextState.emit()

	}

	return nil
}

func (s *EnterDescriptionState) emit() {
	s.emitUpdate(UpdateAll)
}

func (s *EnterDescriptionState) emitUpdate(update string) {
	s.update = update
	s.listener.OnEnterDescription(s)
}

// -------------------------------------------------------------------------------------------------

type ValidateState struct {
	*Resolved
	*AmountInfo
	Note string
}

func (s *ValidateState) Continue() error {

	amountInSat := s.Amount.toBtc()
	if s.TakeFeeFromAmount {
		amountInSat = btcutil.Amount(s.TotalBalance.InSat)
	}

	inputCurrency := s.Amount.InInputCurrency.Currency

	analyzer := newPaymentAnalyzer(s.PaymentContext)

	var analysis *operation.PaymentAnalysis
	var err error

	analysis, err = analyzer.ToAddress(&operation.PaymentToAddress{
		TakeFeeFromAmount:     s.TakeFeeFromAmount,
		AmountInSat:           int64(amountInSat),
		FeeRateInSatsPerVByte: s.FeeRateInSatsPerVByte,
	})
	if err != nil {
		return err
	}

	switch analysis.Status {
	case operation.AnalysisStatusOk:
		return s.emitAnalysisOk(analysis, false)

	case operation.AnalysisStatusUnpayable:

		// redo the analysis with min fee rate
		minFeeAnalyzer := newPaymentAnalyzer(s.PaymentContext)
		minFeeAnalysis, err := minFeeAnalyzer.ToAddress(&operation.PaymentToAddress{
			TakeFeeFromAmount:     s.TakeFeeFromAmount,
			AmountInSat:           int64(amountInSat),
			FeeRateInSatsPerVByte: s.PaymentContext.MinFeeRateInSatsPerVByte,
		})
		if err != nil {
			return err
		}

		switch minFeeAnalysis.Status {
		case operation.AnalysisStatusOk:
			// The UI expects to show the full unpayable fee, so we still
			// use the failed analysis here.
			return s.emitAnalysisOk(analysis, true)

			// We couldn't even compute the minimum fee, amount must be above balance. We'll
			// show the minimum fee for all funds (which is, of course, a lie):
		case operation.AnalysisStatusUnpayable:

			// We'll do a "best effort" guess of what minimum balance the user would
			// need to pay for the requested amount, even though that's not real or
			// accurate (its impossible to calculate the fee for an amount greater na
			// than the sum of the utxos, because the fee depends on the number and
			// the type of the utxos used). Our best guess is calculating the fee of a
			// use all funds transaction and adding that to the requested amount.

			return s.emitBalanceError(OperationErrorUnpayable, minFeeAnalysis, inputCurrency)
		}

	case operation.AnalysisStatusAmountGreaterThanBalance,
		operation.AnalysisStatusAmountTooSmall:

		switch analysis.Status {
		case operation.AnalysisStatusUnpayable:
			return s.emitBalanceError(OperationErrorUnpayable, analysis, inputCurrency)

		case operation.AnalysisStatusAmountGreaterThanBalance:
			return s.emitBalanceError(OperationErrorAmountGreaterThanBalance, analysis, inputCurrency)

		case operation.AnalysisStatusAmountTooSmall:
			return s.emitError(OperationErrorAmountTooSmall)
		}

	default:
		return fmt.Errorf("unrecognized analysis status: %v", analysis.Status)
	}

	return nil
}

func (s *ValidateState) emitAnalysisOk(analysis *operation.PaymentAnalysis, feeNeedsChange bool) error {

	amount := s.Amount
	if s.TakeFeeFromAmount {
		amount = s.PaymentContext.toBitcoinAmount(
			analysis.AmountInSat,
			s.Amount.InInputCurrency.Currency,
		)
	}
	fee := s.PaymentContext.toBitcoinAmount(
		analysis.FeeInSat,
		s.Amount.InInputCurrency.Currency,
	)
	validated := &Validated{
		analysis:       analysis,
		Fee:            fee,
		FeeNeedsChange: feeNeedsChange,
		Total:          amount.add(fee),
	}

	amountInfo := s.AmountInfo.mutating(func(info *AmountInfo) {
		info.Amount = amount
	})

	if s.PaymentIntent.URI.Message != "" || s.Note != "" {

		note := s.Note
		if note == "" {
			note = s.PaymentIntent.URI.Message
		}

		nextState := &ConfirmState{
			Resolved:   s.Resolved,
			AmountInfo: amountInfo,
			Validated:  validated,
			Note:       note,
		}
		nextState.emitUpdate(s.update)

	} else {

		nextState := &EnterDescriptionState{
			Resolved:   s.Resolved,
			AmountInfo: amountInfo,
			Validated:  validated,
		}
		nextState.emit()
	}

	return nil
}

func (s *ValidateState) emit() {
	s.emitUpdate(UpdateAll)
}

func (s *ValidateState) emitUpdate(update string) {
	s.update = update
	s.listener.OnValidate(s)
}

// -------------------------------------------------------------------------------------------------

type ValidateLightningState struct {
	*Resolved
	*AmountInfo
	Note string
}

func (s *ValidateLightningState) Continue() error {

	amountInSat := s.Amount.toBtc()
	if s.TakeFeeFromAmount {
		amountInSat = btcutil.Amount(s.TotalBalance.InSat)
	}

	inputCurrency := s.Amount.InInputCurrency.Currency

	swap := s.PaymentContext.SubmarineSwap

	analyzer := newPaymentAnalyzer(s.PaymentContext)
	analysis, err := analyzer.ToInvoice(&operation.PaymentToInvoice{
		TakeFeeFromAmount:     s.TakeFeeFromAmount,
		AmountInSat:           int64(amountInSat),
		SwapFees:              swap.Fees.toInternalType(),
		BestRouteFees:         swap.toBestRouteFeesInternalType(),
		FundingOutputPolicies: swap.FundingOutputPolicies.toInternalType(),
	})
	if err != nil {
		return err
	}

	switch analysis.Status {
	case operation.AnalysisStatusOk:

		s.emitAnalysisOk(analysis)

	case operation.AnalysisStatusUnpayable:

		return s.emitBalanceError(OperationErrorUnpayable, analysis, inputCurrency)

	case operation.AnalysisStatusAmountGreaterThanBalance:

		return s.emitBalanceError(OperationErrorAmountGreaterThanBalance, analysis, inputCurrency)

	case operation.AnalysisStatusAmountTooSmall:
		return s.emitError(OperationErrorAmountTooSmall)

	default:
		return fmt.Errorf("unrecognized analysis status: %v", analysis.Status)
	}

	return nil
}

func (s *ValidateLightningState) emitAnalysisOk(analysis *operation.PaymentAnalysis) {
	note := s.Note
	if note != "" {
		note = s.PaymentIntent.URI.Invoice.Description
	}

	amount := s.Amount
	if s.TakeFeeFromAmount {
		amount = s.PaymentContext.toBitcoinAmount(
			analysis.AmountInSat,
			s.Amount.InInputCurrency.Currency,
		)
	}

	onchainFee := s.PaymentContext.toBitcoinAmount(
		analysis.FeeInSat,
		s.Amount.InInputCurrency.Currency,
	)

	swapFees := newSwapFeesFromInternal(analysis.SwapFees)
	var offchainFee *BitcoinAmount
	var totalFee *BitcoinAmount

	if swapFees.DebtType == DebtTypeLend {
		offchainFee = s.PaymentContext.toBitcoinAmount(
			swapFees.RoutingFeeInSat,
			s.Amount.InInputCurrency.Currency,
		)
		totalFee = offchainFee
	} else {
		offchainFee = s.PaymentContext.toBitcoinAmount(
			swapFees.RoutingFeeInSat+swapFees.OutputPaddingInSat,
			s.Amount.InInputCurrency.Currency,
		)
		totalFee = onchainFee.add(offchainFee)
	}

	isOneConf := analysis.SwapFees.ConfirmationsNeeded > 0

	validated := &Validated{
		Fee:      totalFee,
		Total:    amount.add(totalFee),
		analysis: analysis,
		SwapInfo: &SwapInfo{
			OnchainFee: onchainFee,
			SwapFees:   swapFees,
			IsOneConf:  isOneConf,
		},
	}

	amountInfo := s.AmountInfo.mutating(func(info *AmountInfo) {
		info.Amount = amount
	})

	if note != "" {

		nextState := &ConfirmLightningState{
			Resolved:   s.Resolved,
			AmountInfo: amountInfo,
			Validated:  validated,
			Note:       s.Note,
		}
		nextState.emit()

	} else {

		nextState := &EnterDescriptionState{
			Resolved:   s.Resolved,
			AmountInfo: amountInfo,
			Validated:  validated,
		}
		nextState.emit()
	}
}

func (s *ValidateLightningState) emit() {
	s.emitUpdate(UpdateAll)
}

func (s *ValidateLightningState) emitUpdate(update string) {
	s.update = update
	s.listener.OnValidateLightning(s)
}

// -------------------------------------------------------------------------------------------------

const (
	OperationErrorUnpayable                = "Unpayable"
	OperationErrorAmountGreaterThanBalance = "AmountGreaterThanBalance"
	OperationErrorAmountTooSmall           = "AmountTooSmall"
	OperationErrorInvalidAddress           = "InvalidAddress"
	OperationErrorInvoiceExpired           = "InvoiceExpired"
)

type ErrorState struct {
	BaseState
	PaymentIntent *PaymentIntent
	Error         string
}

func (s *ErrorState) emit() {
	s.listener.OnError(s)
}

type BalanceErrorState struct {
	BaseState
	PaymentIntent *PaymentIntent
	TotalAmount   *MonetaryAmount
	Balance       *MonetaryAmount
	Error         string
}

func (s *BalanceErrorState) emit() {
	s.listener.OnBalanceError(s)
}

// -------------------------------------------------------------------------------------------------

type ConfirmState struct {
	*Resolved
	*AmountInfo
	*Validated
	Note string
}

func (s *ConfirmState) OpenFeeEditor() error {

	maxFeeRate := newPaymentAnalyzer(s.PaymentContext).MaxFeeRateToAddress(&operation.PaymentToAddress{
		TakeFeeFromAmount:     s.TakeFeeFromAmount,
		AmountInSat:           s.Amount.InSat,
		FeeRateInSatsPerVByte: s.FeeRateInSatsPerVByte,
	})

	next := &EditFeeState{
		Resolved:                 s.Resolved,
		AmountInfo:               s.AmountInfo,
		Validated:                s.Validated,
		Note:                     s.Note,
		MaxFeeRateInSatsPerVByte: maxFeeRate,
	}

	next.emit()

	return nil
}

func (s *ConfirmState) Back() error {

	nextState := &EnterDescriptionState{
		Resolved:   s.Resolved,
		AmountInfo: s.AmountInfo,
		Validated:  s.Validated,
		Note:       s.Note,
	}
	nextState.emit()

	return nil
}

func (s *ConfirmState) emit() {
	s.emitUpdate(UpdateAll)
}

func (s *ConfirmState) emitUpdate(update string) {
	s.update = update
	s.listener.OnConfirm(s)
}

// -------------------------------------------------------------------------------------------------

type EditFeeState struct {
	*Resolved
	*AmountInfo
	*Validated
	Note                     string
	MaxFeeRateInSatsPerVByte float64
}

func (s *EditFeeState) MinFeeRateForTarget(target int) (float64, error) {
	feeWindow := s.PaymentContext.FeeWindow.toInternalType()
	return feeWindow.MinimumFeeRate(uint(target))
}

// TODO this currently ignores and forgets input currency, which is important for amount display
// logic in Edit Fee screens
func (s *EditFeeState) CalculateFee(rateInSatsPerVByte float64) (*FeeState, error) {
	amountInSat := s.Amount.InSat
	if s.TakeFeeFromAmount {
		amountInSat = s.TotalBalance.InSat
	}

	analyzer := newPaymentAnalyzer(s.PaymentContext)
	analysis, err := analyzer.ToAddress(&operation.PaymentToAddress{
		TakeFeeFromAmount:     s.TakeFeeFromAmount,
		AmountInSat:           amountInSat,
		FeeRateInSatsPerVByte: rateInSatsPerVByte,
	})
	if err != nil {
		return nil, err
	}

	// TODO(newop): add targetblock to analysis result
	switch analysis.Status {
	// TODO: this should never happen, right? It should have been detected earlier
	case operation.AnalysisStatusAmountGreaterThanBalance, operation.AnalysisStatusAmountTooSmall:
		return &FeeState{
			State: FeeStateNoPossibleFee,
		}, nil
	case operation.AnalysisStatusUnpayable:
		return &FeeState{
			State:              FeeStateNeedsChange,
			Amount:             s.toBitcoinAmount(analysis.FeeInSat),
			RateInSatsPerVByte: rateInSatsPerVByte,
			TargetBlocks:       s.PaymentContext.FeeWindow.nextHighestBlock(rateInSatsPerVByte),
		}, nil
	case operation.AnalysisStatusOk:
		return &FeeState{
			State:              FeeStateFinalFee,
			Amount:             s.toBitcoinAmount(analysis.FeeInSat),
			RateInSatsPerVByte: rateInSatsPerVByte,
			TargetBlocks:       s.PaymentContext.FeeWindow.nextHighestBlock(rateInSatsPerVByte),
		}, nil
	default:
		return nil, fmt.Errorf("unrecognized analysis status: %v", analysis.Status)
	}
}

func (s *EditFeeState) SetFeeRate(rateInSatsPerVByte float64) error {
	// We deref to copy before mutating
	amountInfo := s.AmountInfo.mutating(func(info *AmountInfo) {
		info.FeeRateInSatsPerVByte = rateInSatsPerVByte
	})

	nextState := &ValidateState{
		Resolved:   s.Resolved,
		AmountInfo: amountInfo,
		Note:       s.Note,
	}
	nextState.emitUpdate(UpdateInPlace)

	return nil
}

func (s *EditFeeState) CloseEditor() error {
	nextState := &ValidateState{
		Resolved:   s.Resolved,
		AmountInfo: s.AmountInfo,
		Note:       s.Note,
	}
	nextState.emit()

	return nil
}

func (s *EditFeeState) toBitcoinAmount(sats int64) *BitcoinAmount {
	return NewMonetaryAmountFromSatoshis(sats).toBitcoinAmount(
		s.PaymentContext.ExchangeRateWindow,
		s.PaymentContext.PrimaryCurrency,
	)
}

func (s *EditFeeState) emit() {
	s.listener.OnEditFee(s)
}

// -------------------------------------------------------------------------------------------------

type ConfirmLightningState struct {
	*Resolved
	*AmountInfo
	*Validated
	Note string
}

func (s *ConfirmLightningState) Back() error {

	nextState := &EnterDescriptionState{
		Resolved:   s.Resolved,
		AmountInfo: s.AmountInfo,
		Validated:  s.Validated,
		Note:       s.Note,
	}
	nextState.emit()

	return nil
}

func (s *ConfirmLightningState) emit() {
	s.emitUpdate(UpdateAll)
}

func (s *ConfirmLightningState) emitUpdate(update string) {
	s.update = update
	s.listener.OnConfirmLightning(s)
}

// -------------------------------------------------------------------------------------------------

type AbortState struct {
	BaseState
	PreviousState interface {
		emitUpdate(update string)
	}
}

func (s *AbortState) emit() {
	s.listener.OnAbort(s)
}

func (s *AbortState) Cancel() {
	nextState := s.PreviousState
	nextState.emitUpdate(UpdateEmpty)
}
