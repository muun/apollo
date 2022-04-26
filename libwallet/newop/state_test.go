package newop

import (
	"testing"
	"time"

	"github.com/muun/libwallet"
	"github.com/shopspring/decimal"
)

type testListener struct {
	ch chan State
}

func newTestListener() *testListener {
	return &testListener{ch: make(chan State, 10)}
}

func (t *testListener) OnStart(nextState *StartState)                         { t.ch <- nextState }
func (t *testListener) OnResolve(nextState *ResolveState)                     { t.ch <- nextState }
func (t *testListener) OnEnterAmount(nextState *EnterAmountState)             { t.ch <- nextState }
func (t *testListener) OnEnterDescription(nextState *EnterDescriptionState)   { t.ch <- nextState }
func (t *testListener) OnValidate(nextState *ValidateState)                   { t.ch <- nextState }
func (t *testListener) OnValidateLightning(nextState *ValidateLightningState) { t.ch <- nextState }
func (t *testListener) OnConfirm(nextState *ConfirmState)                     { t.ch <- nextState }
func (t *testListener) OnConfirmLightning(nextState *ConfirmLightningState)   { t.ch <- nextState }
func (t *testListener) OnEditFee(nextState *EditFeeState)                     { t.ch <- nextState }
func (t *testListener) OnError(nextState *ErrorState)                         { t.ch <- nextState }
func (t *testListener) OnBalanceError(nextState *BalanceErrorState)           { t.ch <- nextState }
func (t *testListener) OnAbort(nextState *AbortState)                         { t.ch <- nextState }

func (t *testListener) next() State {
	return <-t.ch
}

var _ TransitionListener = &testListener{}

var testContext = createContext()

func createContext() *PaymentContext {
	var context = &PaymentContext{
		NextTransactionSize: &NextTransactionSize{},
		ExchangeRateWindow: &ExchangeRateWindow{
			rates: make(map[string]float64),
		},
		FeeWindow:                &FeeWindow{},
		PrimaryCurrency:          "BTC",
		MinFeeRateInSatsPerVByte: 1.0,
	}
	context.NextTransactionSize.AddSizeForAmount(&SizeForAmount{
		AmountInSat: 100_000_000,
		SizeInVByte: 240,
	})

	context.ExchangeRateWindow.AddRate("BTC", 1)
	context.ExchangeRateWindow.AddRate("USD", 32_000)
	context.ExchangeRateWindow.AddRate("ARS", 9_074_813.98)

	context.FeeWindow.PutTargetedFees(1, 400.0)
	context.FeeWindow.PutTargetedFees(15, 120.0)
	context.FeeWindow.PutTargetedFees(90, 8.0)

	return context
}

//goland:noinspection GoUnhandledErrorResult
func TestBarebonesOnChainFixedAmountFixedFee(t *testing.T) {

	listener := newTestListener()
	startState := NewOperationFlow(listener)

	startState.Resolve("bitcoin:bcrt1qj35fkq34xend9w0ssthn432vl9pxxsuy0epzlu?amount=0.1&description=foo", libwallet.Regtest())

	resolveState := listener.next().(*ResolveState)

	resolveState.SetContext(testContext)

	validateState := listener.next().(*ValidateState)
	validateState.Continue()

	enterDescriptionState := listener.next().(*EnterDescriptionState)
	enterDescriptionState.EnterDescription("bar")

	confirmState := listener.next().(*ConfirmState)

	if confirmState.Note != "bar" {
		t.Fatalf("expected note to match input, got '%v'", confirmState.Note)
	}
	if confirmState.Amount.InInputCurrency.String() != "0.1 BTC" {
		t.Fatalf("expected amount to match resolved URI, got %v", confirmState.Amount.InInputCurrency)
	}
	if confirmState.Fee.InInputCurrency.String() != "0.00096 BTC" {
		t.Fatalf("expected fee to match, got %v", confirmState.Fee.InInputCurrency)
	}
	if confirmState.Total.InInputCurrency.String() != "0.10096 BTC" {
		t.Fatalf("expected total to match, got %v", confirmState.Total.InInputCurrency)
	}
}

//goland:noinspection GoUnhandledErrorResult
func TestBarebonesOnChainFixedAmountFixedDescriptionFixedFee(t *testing.T) {

	listener := newTestListener()
	startState := NewOperationFlow(listener)

	startState.Resolve("bitcoin:bcrt1qj35fkq34xend9w0ssthn432vl9pxxsuy0epzlu?amount=0.1&message=foo", libwallet.Regtest())

	resolveState := listener.next().(*ResolveState)

	resolveState.SetContext(testContext)

	validateState := listener.next().(*ValidateState)
	validateState.Continue()

	confirmState := listener.next().(*ConfirmState)

	if confirmState.Note != "foo" {
		t.Fatalf("expected note to match input, got '%v'", confirmState.Note)
	}
	if confirmState.Amount.InInputCurrency.String() != "0.1 BTC" {
		t.Fatalf("expected amount to match resolved URI, got %v", confirmState.Amount.InInputCurrency)
	}
	if confirmState.Fee.InInputCurrency.String() != "0.00096 BTC" {
		t.Fatalf("expected fee to match, got %v", confirmState.Fee.InInputCurrency)
	}
	if confirmState.Total.InInputCurrency.String() != "0.10096 BTC" {
		t.Fatalf("expected total to match, got %v", confirmState.Total.InInputCurrency)
	}
}

//goland:noinspection GoUnhandledErrorResult
func TestOnChainFixedAmountChangeFee(t *testing.T) {
	listener := newTestListener()
	startState := NewOperationFlow(listener)

	startState.Resolve("bitcoin:bcrt1qj35fkq34xend9w0ssthn432vl9pxxsuy0epzlu?amount=0.1&description=foo", libwallet.Regtest())

	resolveState := listener.next().(*ResolveState)

	resolveState.SetContext(testContext)

	validateState := listener.next().(*ValidateState)
	validateState.Continue()

	enterDescriptionState := listener.next().(*EnterDescriptionState)
	enterDescriptionState.EnterDescription("bar")

	confirmState := listener.next().(*ConfirmState)

	if confirmState.FeeRateInSatsPerVByte != 400 {
		t.Fatalf("expected initial fee rate to be 400, got %v", confirmState.FeeRateInSatsPerVByte)
	}

	newFeeRate := 15.0

	confirmState.OpenFeeEditor()

	editFeeState := listener.next().(*EditFeeState)

	feeState, err := editFeeState.CalculateFee(newFeeRate)
	if err != nil {
		t.Fatal(err)
	}
	if feeState.State != FeeStateFinalFee {
		t.Fatalf("expected fee state to be FinalFee, got %v", feeState.State)
	}
	if feeState.Amount.InSat != 3600 {
		t.Fatalf("expected fee amount to be 3600, got %v", feeState.Amount.InSat)
	}

	editFeeState.SetFeeRate(newFeeRate)

	validateState = listener.next().(*ValidateState)
	validateState.Continue()

	confirmState = listener.next().(*ConfirmState)

	if confirmState.Note != "bar" {
		t.Fatalf("expected note to match input, got '%v'", confirmState.Note)
	}
	if confirmState.Amount.InInputCurrency.String() != "0.1 BTC" {
		t.Fatalf("expected amount to match resolved URI, got %v", confirmState.Amount.InInputCurrency)
	}
	if confirmState.Fee.InInputCurrency.String() != "0.000036 BTC" {
		t.Fatalf("expected fee to match, got %v", confirmState.Fee.InInputCurrency)
	}
	if confirmState.Total.InInputCurrency.String() != "0.100036 BTC" {
		t.Fatalf("expected total to match, got %v", confirmState.Total.InInputCurrency)
	}
}

//goland:noinspection GoUnhandledErrorResult
func TestOnChainFixedAmountFeeNeedsChange(t *testing.T) {
	listener := newTestListener()
	startState := NewOperationFlow(listener)

	startState.Resolve("bitcoin:bcrt1qj35fkq34xend9w0ssthn432vl9pxxsuy0epzlu?amount=0.9999&description=foo", libwallet.Regtest())

	resolveState := listener.next().(*ResolveState)

	resolveState.SetContext(testContext)

	validateState := listener.next().(*ValidateState)
	validateState.Continue()

	enterDescriptionState := listener.next().(*EnterDescriptionState)
	enterDescriptionState.EnterDescription("bar")

	confirmState := listener.next().(*ConfirmState)

	if confirmState.FeeRateInSatsPerVByte != 400 {
		t.Fatalf("expected initial fee rate to be 400, got %v", confirmState.FeeRateInSatsPerVByte)
	}

	if !confirmState.FeeNeedsChange {
		t.Fatalf("expected initial fee to be unpayable and need changing, got %v which is unpayable", confirmState.FeeRateInSatsPerVByte)
	}

	newFeeRate := 15.0

	confirmState.OpenFeeEditor()

	editFeeState := listener.next().(*EditFeeState)

	feeState, err := editFeeState.CalculateFee(newFeeRate)
	if err != nil {
		t.Fatal(err)
	}
	if feeState.State != FeeStateFinalFee {
		t.Fatalf("expected fee state to be FinalFee, got %v", feeState.State)
	}
	if feeState.Amount.InSat != 3600 {
		t.Fatalf("expected fee amount to be 3600, got %v", feeState.Amount.InSat)
	}

	editFeeState.SetFeeRate(newFeeRate)

	validateState = listener.next().(*ValidateState)
	validateState.Continue()

	confirmState = listener.next().(*ConfirmState)

	if confirmState.FeeNeedsChange {
		t.Fatalf("expected fee to be payable, got %v which is not unpayable", confirmState.FeeRateInSatsPerVByte)
	}

	if confirmState.Note != "bar" {
		t.Fatalf("expected note to match input, got '%v'", confirmState.Note)
	}
	if confirmState.Amount.InInputCurrency.String() != "0.9999 BTC" {
		t.Fatalf("expected amount to match resolved URI, got %v", confirmState.Amount.InInputCurrency)
	}
	if confirmState.Fee.InInputCurrency.String() != "0.000036 BTC" {
		t.Fatalf("expected fee to match, got %v", confirmState.Fee.InInputCurrency)
	}
	if confirmState.Total.InInputCurrency.String() != "0.999936 BTC" {
		t.Fatalf("expected total to match, got %v", confirmState.Total.InInputCurrency)
	}
}

//goland:noinspection GoUnhandledErrorResult
func TestOnChainFixedAmountNoPossibleFee(t *testing.T) {
	listener := newTestListener()
	startState := NewOperationFlow(listener)

	startState.Resolve("bitcoin:bcrt1qj35fkq34xend9w0ssthn432vl9pxxsuy0epzlu?amount=0.9999999&description=foo", libwallet.Regtest())

	resolveState := listener.next().(*ResolveState)

	resolveState.SetContext(testContext)

	validateState := listener.next().(*ValidateState)
	validateState.Continue()

	balanceErrorState := listener.next().(*BalanceErrorState)

	if balanceErrorState.Error != OperationErrorUnpayable {
		t.Fatalf("expected initial fee to be unpayable but got %s", balanceErrorState.Error)
	}

	if balanceErrorState.TotalAmount.String() != "1.0000023 BTC" {
		t.Fatalf("expected total to match, got %v", balanceErrorState.TotalAmount)
	}
}

//goland:noinspection GoUnhandledErrorResult
func TestOnChainFixedAmountTooSmall(t *testing.T) {
	listener := newTestListener()
	startState := NewOperationFlow(listener)

	startState.Resolve("bitcoin:bcrt1qj35fkq34xend9w0ssthn432vl9pxxsuy0epzlu?amount=0.0000004&description=foo", libwallet.Regtest())

	resolveState := listener.next().(*ResolveState)

	resolveState.SetContext(testContext)

	errorState := listener.next().(*ErrorState)
	if errorState.Error != OperationErrorAmountTooSmall {
		t.Fatalf("expected amount to be too small but got %s", errorState.Error)
	}
}

//goland:noinspection GoUnhandledErrorResult
func TestOnChainFixedAmountGreaterThanbalance(t *testing.T) {
	listener := newTestListener()
	startState := NewOperationFlow(listener)

	startState.Resolve("bitcoin:bcrt1qj35fkq34xend9w0ssthn432vl9pxxsuy0epzlu?amount=2.0&description=foo", libwallet.Regtest())

	resolveState := listener.next().(*ResolveState)

	resolveState.SetContext(testContext)

	validateState := listener.next().(*ValidateState)
	validateState.Continue()

	balanceErrorState := listener.next().(*BalanceErrorState)

	if balanceErrorState.Error != OperationErrorAmountGreaterThanBalance {
		t.Fatalf("expected amount to be too small but got %s", balanceErrorState.Error)
	}

	if balanceErrorState.TotalAmount.String() != "2 BTC" {
		t.Fatalf("expected total to match, got %v", balanceErrorState.TotalAmount)
	}
}

//goland:noinspection GoUnhandledErrorResult
func TestOnChainSendZeroFundsWithZeroBalance(t *testing.T) {

	listener := newTestListener()
	startState := NewOperationFlow(listener)

	startState.Resolve("bitcoin:bcrt1qj35fkq34xend9w0ssthn432vl9pxxsuy0epzlu", libwallet.Regtest())

	context := createContext()
	context.NextTransactionSize = &NextTransactionSize{}

	resolveState := listener.next().(*ResolveState)
	resolveState.SetContext(context)

	enterAmountState := listener.next().(*EnterAmountState)
	enterAmountState.EnterAmount(NewMonetaryAmountFromSatoshis(0), true)

	validateState := listener.next().(*ValidateState)
	validateState.Continue()

	errorState := listener.next().(*ErrorState)

	if errorState.Error != OperationErrorAmountTooSmall {
		t.Fatalf("expected error to be amount too small but got %s", errorState.Error)
	}
}

//goland:noinspection GoUnhandledErrorResult
func TestOnChainTFFA(t *testing.T) {

	listener := newTestListener()
	startState := NewOperationFlow(listener)

	startState.Resolve("bitcoin:bcrt1qj35fkq34xend9w0ssthn432vl9pxxsuy0epzlu", libwallet.Regtest())

	resolveState := listener.next().(*ResolveState)
	resolveState.SetContext(testContext)

	enterAmountState := listener.next().(*EnterAmountState)
	enterAmountState.EnterAmount(NewMonetaryAmountFromSatoshis(100_000_000), true)

	validateState := listener.next().(*ValidateState)
	validateState.Continue()

	enterDescriptionState := listener.next().(*EnterDescriptionState)
	enterDescriptionState.EnterDescription("bar")
	if enterDescriptionState.Amount.InInputCurrency.String() != "0.99904 BTC" {
		t.Fatalf("expected amount to match input, got %v", enterDescriptionState.Amount.InInputCurrency)
	}

	confirmState := listener.next().(*ConfirmState)

	if confirmState.Note != "bar" {
		t.Fatalf("expected note to match input, got '%v'", confirmState.Note)
	}
	if confirmState.Amount.InInputCurrency.String() != "0.99904 BTC" {
		t.Fatalf("expected amount to match input, got %v", confirmState.Amount.InInputCurrency)
	}
	if confirmState.Fee.InInputCurrency.String() != "0.00096 BTC" {
		t.Fatalf("expected fee to match, got %v", confirmState.Fee.InInputCurrency)
	}
	if confirmState.Total.InInputCurrency.String() != "1 BTC" {
		t.Fatalf("expected total to match, got %v", confirmState.Total.InInputCurrency)
	}

	confirmState.Back()

	// check we preserve values correctly
	enterDescriptionState = listener.next().(*EnterDescriptionState)
	if enterDescriptionState.Note != "bar" {
		t.Fatalf("expected note to match input, got '%v'", confirmState.Note)
	}
	if enterDescriptionState.Amount.InInputCurrency.String() != "0.99904 BTC" {
		t.Fatalf("expected amount to match input, got %v", enterDescriptionState.Amount.InInputCurrency)
	}
}

//goland:noinspection GoUnhandledErrorResult
func TestInvalidAmountEmitsInvalidAddress(t *testing.T) {
	listener := newTestListener()
	startState := NewOperationFlow(listener)

	startState.Resolve("bitcoin:bcrt1qj35fkq34xend9w0ssthn432vl9pxxsuy0epzlu?amount=bananabanana", libwallet.Regtest())

	resolveState := listener.next().(*ResolveState)

	resolveState.SetContext(testContext)

	errorState := listener.next().(*ErrorState)

	if errorState.Error != OperationErrorInvalidAddress {
		t.Fatalf("expected error to be invalid address but got %s", errorState.Error)
	}
}

//goland:noinspection GoUnhandledErrorResult
func TestOnChainBack(t *testing.T) {

	listener := newTestListener()
	startState := NewOperationFlow(listener)

	startState.Resolve("bitcoin:bcrt1qj35fkq34xend9w0ssthn432vl9pxxsuy0epzlu", libwallet.Regtest())

	resolveState := listener.next().(*ResolveState)
	resolveState.SetContext(testContext)

	enterAmountState := listener.next().(*EnterAmountState)
	enterAmountState.EnterAmount(NewMonetaryAmountFromSatoshis(100_000_000), true)

	validateState := listener.next().(*ValidateState)
	validateState.Continue()

	enterDescriptionState := listener.next().(*EnterDescriptionState)
	enterDescriptionState.EnterDescription("bar")

	confirmState := listener.next().(*ConfirmState)
	confirmState.Back()

	enterDescriptionState = listener.next().(*EnterDescriptionState)
	enterDescriptionState.Back()

	enterAmountState = listener.next().(*EnterAmountState)
	// TODO when deleting this method impl (deprecated) rm lines below up until the call to ChangeCurrencyWithAmount
	enterAmountState.ChangeCurrency("USD")

	enterAmountState = listener.next().(*EnterAmountState)
	enterAmountState.Back()

	abortState := listener.next().(*AbortState)
	if abortState.update != UpdateAll {
		t.Fatalf("expected normal/full update , got %v", abortState.update)
	}
	abortState.Cancel()

	enterAmountState = listener.next().(*EnterAmountState)
	if enterAmountState.update != UpdateEmpty {
		t.Fatalf("expected empty update, got %v", enterAmountState.update)
	}
	enterAmountState.Back()

	abortState = listener.next().(*AbortState)
	if abortState.update != UpdateAll {
		t.Fatalf("expected normal/full update , got %v", abortState.update)
	}
	abortState.Cancel()

	enterAmountState = listener.next().(*EnterAmountState)
	enterAmountState.ChangeCurrencyWithAmount("USD", NewMonetaryAmountFromSatoshis(1_000_000))

	enterAmountState = listener.next().(*EnterAmountState)
	enterAmountState.Back()

	abortState = listener.next().(*AbortState)
	if abortState.update != UpdateAll {
		t.Fatalf("expected normal/full update , got %v", abortState.update)
	}
	abortState.Cancel()

	enterAmountState = listener.next().(*EnterAmountState)
	if enterAmountState.update != UpdateEmpty {
		t.Fatalf("expected empty update, got %v", enterAmountState.update)
	}
	enterAmountState.Back()

	abortState = listener.next().(*AbortState)
	if abortState.update != UpdateAll {
		t.Fatalf("expected normal/full update , got %v", abortState.update)
	}
	abortState.Cancel()

	// One more, just for the giggles
	enterAmountState = listener.next().(*EnterAmountState)
	enterAmountState.Back()

	_ = listener.next().(*AbortState)
}

//goland:noinspection GoUnhandledErrorResult
func TestOnChainChangeCurrency(t *testing.T) {

	listener := newTestListener()
	startState := NewOperationFlow(listener)

	startState.Resolve("bitcoin:bcrt1qj35fkq34xend9w0ssthn432vl9pxxsuy0epzlu", libwallet.Regtest())

	resolveState := listener.next().(*ResolveState)
	resolveState.SetContext(testContext)

	enterAmountState := listener.next().(*EnterAmountState)
	inputAmountCurrency := enterAmountState.Amount.InInputCurrency.Currency
	balanceCurrency := enterAmountState.TotalBalance.InInputCurrency.Currency
	if inputAmountCurrency != balanceCurrency {
		t.Fatalf("expected amount currency (%v) to match balance currency (%v)", inputAmountCurrency, balanceCurrency)
	}
	enterAmountState.EnterAmount(NewMonetaryAmountFromSatoshis(100_000_000), true)

	validateState := listener.next().(*ValidateState)
	validateState.Continue()

	enterDescriptionState := listener.next().(*EnterDescriptionState)
	if enterDescriptionState.Amount.InInputCurrency.String() != "0.99904 BTC" {
		t.Fatalf("expected amount to match input, got %v", enterDescriptionState.Amount.InInputCurrency)
	}

	enterDescriptionState.Back()

	enterAmountState = listener.next().(*EnterAmountState)
	// TODO when deleting this method impl (deprecated) rm lines below up until the call to ChangeCurrencyWithAmount
	enterAmountState.ChangeCurrency("USD")

	enterAmountState = listener.next().(*EnterAmountState)
	if enterAmountState.update != UpdateInPlace {
		t.Fatalf("expected UpdateInPlace, got '%v'", enterAmountState.update)
	}
	if enterAmountState.Amount.InSat != 100_000_000 {
		t.Fatalf("expected amount to match 100_000_000, got '%v'", enterAmountState.Amount.InSat)
	}
	if enterAmountState.Amount.InInputCurrency.String() != "32000 USD" {
		t.Fatalf("expected amount to match 32000 USD, got '%v'", enterAmountState.Amount.InInputCurrency.String())
	}
	if enterAmountState.Amount.InPrimaryCurrency.String() != "1 BTC" {
		t.Fatalf("expected amount to match 1 BTC, got '%v'", enterAmountState.Amount.InPrimaryCurrency.String())
	}

	enterAmountState.ChangeCurrency("BTC")
	enterAmountState = listener.next().(*EnterAmountState)
	if enterAmountState.update != UpdateInPlace {
		t.Fatalf("expected UpdateInPlace, got '%v'", enterAmountState.update)
	}
	if enterAmountState.Amount.InSat != 100_000_000 {
		t.Fatalf("expected amount to match 100_000_000, got '%v'", enterAmountState.Amount.InSat)
	}
	if enterAmountState.Amount.InInputCurrency.String() != "1 BTC" {
		t.Fatalf("expected amount to match 1 BTC, got '%v'", enterAmountState.Amount.InInputCurrency.String())
	}
	if enterAmountState.Amount.InPrimaryCurrency.String() != "1 BTC" {
		t.Fatalf("expected amount to match 1 BTC, got '%v'", enterAmountState.Amount.InPrimaryCurrency.String())
	}

	enterAmountState.ChangeCurrencyWithAmount("USD", NewMonetaryAmountFromSatoshis(1_000_000))
	enterAmountState = listener.next().(*EnterAmountState)
	if enterAmountState.update != UpdateInPlace {
		t.Fatalf("expected UpdateInPlace, got '%v'", enterAmountState.update)
	}
	if enterAmountState.Amount.InSat != 1_000_000 {
		t.Fatalf("expected amount to match 1_000_000, got '%v'", enterAmountState.Amount.InSat)
	}
	if enterAmountState.Amount.InInputCurrency.String() != "320 USD" {
		t.Fatalf("expected amount to match 320 USD, got '%v'", enterAmountState.Amount.InInputCurrency.String())
	}
	if enterAmountState.Amount.InPrimaryCurrency.String() != "0.01 BTC" {
		t.Fatalf("expected amount to match 0.01 BTC, got '%v'", enterAmountState.Amount.InPrimaryCurrency.String())
	}

	enterAmountState.ChangeCurrencyWithAmount("BTC", enterAmountState.Amount.InInputCurrency)
	enterAmountState = listener.next().(*EnterAmountState)
	if enterAmountState.update != UpdateInPlace {
		t.Fatalf("expected UpdateInPlace, got '%v'", enterAmountState.update)
	}
	if enterAmountState.Amount.InSat != 1_000_000 {
		t.Fatalf("expected amount to match 1_000_000, got '%v'", enterAmountState.Amount.InSat)
	}
	if enterAmountState.Amount.InInputCurrency.String() != "0.01 BTC" {
		t.Fatalf("expected amount to match 0.01 BTC, got '%v'", enterAmountState.Amount.InInputCurrency.String())
	}
	if enterAmountState.Amount.InPrimaryCurrency.String() != "0.01 BTC" {
		t.Fatalf("expected amount to match 0.01 BTC, got '%v'", enterAmountState.Amount.InPrimaryCurrency.String())
	}

	enterDescriptionState.EnterDescription("bar")
	confirmState := listener.next().(*ConfirmState)

	if confirmState.Note != "bar" {
		t.Fatalf("expected note to match input, got '%v'", confirmState.Note)
	}
	if confirmState.Amount.InInputCurrency.String() != "0.99904 BTC" {
		t.Fatalf("expected amount to match input, got %v", confirmState.Amount.InInputCurrency)
	}
	if confirmState.Fee.InInputCurrency.String() != "0.00096 BTC" {
		t.Fatalf("expected fee to match, got %v", confirmState.Fee.InInputCurrency)
	}
	if confirmState.Total.InInputCurrency.String() != "1 BTC" {
		t.Fatalf("expected total to match, got %v", confirmState.Total.InInputCurrency)
	}

	confirmState.Back()

	// check we preserve values correctly
	enterDescriptionState = listener.next().(*EnterDescriptionState)
	if enterDescriptionState.Note != "bar" {
		t.Fatalf("expected note to match input, got '%v'", confirmState.Note)
	}
	if enterDescriptionState.Amount.InInputCurrency.String() != "0.99904 BTC" {
		t.Fatalf("expected amount to match input, got %v", enterDescriptionState.Amount.InInputCurrency)
	}
}

//goland:noinspection GoUnhandledErrorResult
func TestLightningSendZeroFunds(t *testing.T) {

	listener := newTestListener()
	startState := NewOperationFlow(listener)

	invoice, err := libwallet.ParseInvoice("lnbcrt1ps3l7zlpp5ngv7sl4wrjalma9navd0w9956pu0tcqwrltcnnzz83eeyk4rszxqdqqcqzpgrzjq2eawnq2ywdmcpe56nk02tfamgfmsn0acp0zcn8z8cr0djkgpslr5qqpkgqqqqgqqqqqqqlgqqqqqqgq9qrzjq2eawnq2ywdmcpe56nk02tfamgfmsn0acp0zcn8z8cr0djkgpslr5qqpkgqqqqsqqqqqqqlgqqqqqqgq9qsp5luyagw4mtcq735je8ldukhlkg063cxzycjhpz2x2hjfq2mgk5xns9qyyssq7ydzdwyl7yr6ldpzqjjspmgrevw4lxt4jwfy3cxm7we20wveqq8p8khjxuq9u3v953e7t9r8ysfzx5r874vu3nd7w5yx5eqfxu0tevspgxr607", libwallet.Regtest())
	if err != nil {
		panic(err)
	}

	startState.ResolveInvoice(invoice, libwallet.Regtest())

	resolveState := listener.next().(*ResolveState)

	context := createContext()
	context.NextTransactionSize = &NextTransactionSize{}
	context.SubmarineSwap = &SubmarineSwap{
		BestRouteFees: []*BestRouteFees{
			{
				MaxCapacity:              100_000,
				FeeProportionalMillionth: 1,
				FeeBase:                  1_000,
			},
		},
		FundingOutputPolicies: &FundingOutputPolicies{
			MaximumDebtInSat:       0,
			PotentialCollectInSat:  0,
			MaxAmountInSatFor0Conf: 25_000,
		},
	}

	resolveState.setContextWithTime(context, time.Unix(1629485164, 0))

	enterAmountState := listener.next().(*EnterAmountState)
	enterAmountState.EnterAmount(NewMonetaryAmountFromSatoshis(0), false)

	validateState := listener.next().(*ValidateLightningState)
	validateState.Continue()

	errorState := listener.next().(*ErrorState)

	if errorState.Error != OperationErrorAmountTooSmall {
		t.Fatalf("expected error to be amount too small but got %s", errorState.Error)
	}
}

//goland:noinspection GoUnhandledErrorResult
func TestLightningSendZeroFundsTFFA(t *testing.T) {

	listener := newTestListener()
	startState := NewOperationFlow(listener)

	invoice, err := libwallet.ParseInvoice("lnbcrt1ps3l7zlpp5ngv7sl4wrjalma9navd0w9956pu0tcqwrltcnnzz83eeyk4rszxqdqqcqzpgrzjq2eawnq2ywdmcpe56nk02tfamgfmsn0acp0zcn8z8cr0djkgpslr5qqpkgqqqqgqqqqqqqlgqqqqqqgq9qrzjq2eawnq2ywdmcpe56nk02tfamgfmsn0acp0zcn8z8cr0djkgpslr5qqpkgqqqqsqqqqqqqlgqqqqqqgq9qsp5luyagw4mtcq735je8ldukhlkg063cxzycjhpz2x2hjfq2mgk5xns9qyyssq7ydzdwyl7yr6ldpzqjjspmgrevw4lxt4jwfy3cxm7we20wveqq8p8khjxuq9u3v953e7t9r8ysfzx5r874vu3nd7w5yx5eqfxu0tevspgxr607", libwallet.Regtest())
	if err != nil {
		panic(err)
	}

	startState.ResolveInvoice(invoice, libwallet.Regtest())

	resolveState := listener.next().(*ResolveState)

	context := createContext()
	context.NextTransactionSize = &NextTransactionSize{}
	context.SubmarineSwap = &SubmarineSwap{
		BestRouteFees: []*BestRouteFees{
			{
				MaxCapacity:              100_000,
				FeeProportionalMillionth: 1,
				FeeBase:                  1_000,
			},
		},
		FundingOutputPolicies: &FundingOutputPolicies{
			MaximumDebtInSat:       0,
			PotentialCollectInSat:  0,
			MaxAmountInSatFor0Conf: 25_000,
		},
	}

	resolveState.setContextWithTime(context, time.Unix(1629485164, 0))

	enterAmountState := listener.next().(*EnterAmountState)
	enterAmountState.EnterAmount(NewMonetaryAmountFromSatoshis(0), true)

	validateState := listener.next().(*ValidateLightningState)
	validateState.Continue()

	errorState := listener.next().(*ErrorState)

	if errorState.Error != OperationErrorAmountTooSmall {
		t.Fatalf("expected error to be amount too small but got %s", errorState.Error)
	}
}

//goland:noinspection GoUnhandledErrorResult
func TestLightningSendNegativeFunds(t *testing.T) {

	listener := newTestListener()
	startState := NewOperationFlow(listener)

	invoice, err := libwallet.ParseInvoice("lnbcrt1ps3l7zlpp5ngv7sl4wrjalma9navd0w9956pu0tcqwrltcnnzz83eeyk4rszxqdqqcqzpgrzjq2eawnq2ywdmcpe56nk02tfamgfmsn0acp0zcn8z8cr0djkgpslr5qqpkgqqqqgqqqqqqqlgqqqqqqgq9qrzjq2eawnq2ywdmcpe56nk02tfamgfmsn0acp0zcn8z8cr0djkgpslr5qqpkgqqqqsqqqqqqqlgqqqqqqgq9qsp5luyagw4mtcq735je8ldukhlkg063cxzycjhpz2x2hjfq2mgk5xns9qyyssq7ydzdwyl7yr6ldpzqjjspmgrevw4lxt4jwfy3cxm7we20wveqq8p8khjxuq9u3v953e7t9r8ysfzx5r874vu3nd7w5yx5eqfxu0tevspgxr607", libwallet.Regtest())
	if err != nil {
		panic(err)
	}

	startState.ResolveInvoice(invoice, libwallet.Regtest())

	resolveState := listener.next().(*ResolveState)

	context := createContext()
	context.NextTransactionSize = &NextTransactionSize{}
	context.SubmarineSwap = &SubmarineSwap{
		BestRouteFees: []*BestRouteFees{
			{
				MaxCapacity:              100_000,
				FeeProportionalMillionth: 1,
				FeeBase:                  1_000,
			},
		},
		FundingOutputPolicies: &FundingOutputPolicies{
			MaximumDebtInSat:       0,
			PotentialCollectInSat:  0,
			MaxAmountInSatFor0Conf: 25_000,
		},
	}

	resolveState.setContextWithTime(context, time.Unix(1629485164, 0))

	enterAmountState := listener.next().(*EnterAmountState)
	enterAmountState.EnterAmount(NewMonetaryAmountFromSatoshis(-10), false)

	validateState := listener.next().(*ValidateLightningState)
	validateState.Continue()

	errorState := listener.next().(*ErrorState)

	if errorState.Error != OperationErrorAmountTooSmall {
		t.Fatalf("expected error to be amount too small but got %s", errorState.Error)
	}
}

//goland:noinspection GoUnhandledErrorResult
func TestLightningSendNegativeFundsWithTFFA(t *testing.T) {

	listener := newTestListener()
	startState := NewOperationFlow(listener)

	invoice, err := libwallet.ParseInvoice("lnbcrt1ps3l7zlpp5ngv7sl4wrjalma9navd0w9956pu0tcqwrltcnnzz83eeyk4rszxqdqqcqzpgrzjq2eawnq2ywdmcpe56nk02tfamgfmsn0acp0zcn8z8cr0djkgpslr5qqpkgqqqqgqqqqqqqlgqqqqqqgq9qrzjq2eawnq2ywdmcpe56nk02tfamgfmsn0acp0zcn8z8cr0djkgpslr5qqpkgqqqqsqqqqqqqlgqqqqqqgq9qsp5luyagw4mtcq735je8ldukhlkg063cxzycjhpz2x2hjfq2mgk5xns9qyyssq7ydzdwyl7yr6ldpzqjjspmgrevw4lxt4jwfy3cxm7we20wveqq8p8khjxuq9u3v953e7t9r8ysfzx5r874vu3nd7w5yx5eqfxu0tevspgxr607", libwallet.Regtest())
	if err != nil {
		panic(err)
	}

	startState.ResolveInvoice(invoice, libwallet.Regtest())

	resolveState := listener.next().(*ResolveState)

	context := createContext()
	context.NextTransactionSize = &NextTransactionSize{}
	context.SubmarineSwap = &SubmarineSwap{
		BestRouteFees: []*BestRouteFees{
			{
				MaxCapacity:              100_000,
				FeeProportionalMillionth: 1,
				FeeBase:                  1_000,
			},
		},
		FundingOutputPolicies: &FundingOutputPolicies{
			MaximumDebtInSat:       0,
			PotentialCollectInSat:  0,
			MaxAmountInSatFor0Conf: 25_000,
		},
	}

	resolveState.setContextWithTime(context, time.Unix(1629485164, 0))

	enterAmountState := listener.next().(*EnterAmountState)
	enterAmountState.EnterAmount(NewMonetaryAmountFromSatoshis(-10), true)

	validateState := listener.next().(*ValidateLightningState)
	validateState.Continue()

	errorState := listener.next().(*ErrorState)

	if errorState.Error != OperationErrorAmountTooSmall {
		t.Fatalf("expected error to be amount too small but got %s", errorState.Error)
	}
}

//goland:noinspection GoUnhandledErrorResult
func TestLightningExpiredInvoice(t *testing.T) {

	listener := newTestListener()
	startState := NewOperationFlow(listener)

	invoice, err := libwallet.ParseInvoice("lnbcrt100u1ps3kdrgpp5klwrzs0u63sqnca8elqu86p98swxycw3fgjtmeddm2ljd7ymrlwsdqqcqzpgsp5zs02vngwrywtqhwygu44wp464lgjtqyc5h76vae073064p72znas9qyyssqz2263utx4n7r7n85s9wg3ma2zmg3xtg46nj3e6nnr6g67tnj6jwn7urvx5qukhqjzmcnuc4t7uqlxhftqwq4hxha3ests23fcmt5evqpazdmg2", libwallet.Regtest())
	if err != nil {
		panic(err)
	}

	startState.ResolveInvoice(invoice, libwallet.Regtest())

	resolveState := listener.next().(*ResolveState)
	resolveState.SetContext(testContext)

	errorState := listener.next().(*ErrorState)

	if errorState.Error != OperationErrorInvoiceExpired {
		t.Fatalf("expected error to match, got '%v'", errorState.Error)
	}
}

//goland:noinspection GoUnhandledErrorResult
func TestLightningInvoiceWithAmount(t *testing.T) {

	listener := newTestListener()
	startState := NewOperationFlow(listener)

	invoice, err := libwallet.ParseInvoice("lnbcrt100u1ps3l5eepp5njeddrlmsg9cd2a4v508mqucz7tdge90vvp4f5n23gh7kthnjjdqdqqcqzpgrzjq2eawnq2ywdmcpe56nk02tfamgfmsn0acp0zcn8z8cr0djkgpslr5qqpkgqqqqgqqqqqqqlgqqqqqqgq9qsp52qtk90062t5mha837ulm77vf04ph4kaxerm8xugjdkp9gk6d8yqs9qyyssqw09stp3vy33dfjc6vcrdfmf58trg5pte6efph9pj9gwlg0w7anhz6aelv0p3r9qj6vrjjw9jyj6s9tjujec2fm9k8ag3yvgvwszswxsqhl6equ", libwallet.Regtest())
	if err != nil {
		panic(err)
	}

	startState.ResolveInvoice(invoice, libwallet.Regtest())

	resolveState := listener.next().(*ResolveState)

	context := createContext()
	context.SubmarineSwap = &SubmarineSwap{
		Fees: &SwapFees{
			RoutingFeeInSat:     0,
			DebtType:            DebtTypeNone,
			DebtAmountInSat:     0,
			OutputAmountInSat:   10000,
			OutputPaddingInSat:  0,
			ConfirmationsNeeded: 0,
		},
	}

	resolveState.setContextWithTime(context, time.Unix(1629475653, 0))

	validateState := listener.next().(*ValidateLightningState)
	validateState.Continue()

	enterDescriptionState := listener.next().(*EnterDescriptionState)
	enterDescriptionState.EnterDescription("foo")

	confirmState := listener.next().(*ConfirmLightningState)

	if confirmState.Note != "foo" {
		t.Fatalf("expected note to match input, got '%v'", confirmState.Note)
	}
	if confirmState.Amount.InInputCurrency.String() != "0.0001 BTC" {
		t.Fatalf("expected amount to match resolved URI, got %v", confirmState.Amount.InInputCurrency)
	}
	if confirmState.Fee.InInputCurrency.String() != "0.0000192 BTC" {
		t.Fatalf("expected fee to match, got %v", confirmState.Fee.InInputCurrency)
	}
	if confirmState.Total.InInputCurrency.String() != "0.0001192 BTC" {
		t.Fatalf("expected total to match, got %v", confirmState.Total.InInputCurrency)
	}
}

//goland:noinspection GoUnhandledErrorResult
func TestLightningWithAmountBack(t *testing.T) {

	listener := newTestListener()
	startState := NewOperationFlow(listener)

	invoice, err := libwallet.ParseInvoice("lnbcrt100u1ps3l5eepp5njeddrlmsg9cd2a4v508mqucz7tdge90vvp4f5n23gh7kthnjjdqdqqcqzpgrzjq2eawnq2ywdmcpe56nk02tfamgfmsn0acp0zcn8z8cr0djkgpslr5qqpkgqqqqgqqqqqqqlgqqqqqqgq9qsp52qtk90062t5mha837ulm77vf04ph4kaxerm8xugjdkp9gk6d8yqs9qyyssqw09stp3vy33dfjc6vcrdfmf58trg5pte6efph9pj9gwlg0w7anhz6aelv0p3r9qj6vrjjw9jyj6s9tjujec2fm9k8ag3yvgvwszswxsqhl6equ", libwallet.Regtest())
	if err != nil {
		panic(err)
	}

	startState.ResolveInvoice(invoice, libwallet.Regtest())

	resolveState := listener.next().(*ResolveState)

	context := createContext()
	context.SubmarineSwap = &SubmarineSwap{
		Fees: &SwapFees{
			RoutingFeeInSat:     0,
			DebtType:            DebtTypeNone,
			DebtAmountInSat:     0,
			OutputAmountInSat:   10000,
			OutputPaddingInSat:  0,
			ConfirmationsNeeded: 0,
		},
	}

	resolveState.setContextWithTime(context, time.Unix(1629475653, 0))

	validateState := listener.next().(*ValidateLightningState)
	validateState.Continue()

	enterDescriptionState := listener.next().(*EnterDescriptionState)
	enterDescriptionState.EnterDescription("foo")

	confirmState := listener.next().(*ConfirmLightningState)

	confirmState.Back()
	enterDescriptionState = listener.next().(*EnterDescriptionState)
	if enterDescriptionState.Note != "foo" {
		t.Fatalf("expected note to match input, got '%v'", enterDescriptionState.Note)
	}

	enterDescriptionState.EnterDescription("bar")
	confirmState = listener.next().(*ConfirmLightningState)

	if confirmState.Note != "bar" {
		t.Fatalf("expected note to match input, got '%v'", confirmState.Note)
	}
	if confirmState.Amount.InInputCurrency.String() != "0.0001 BTC" {
		t.Fatalf("expected amount to match resolved URI, got %v", confirmState.Amount.InInputCurrency)
	}
	if confirmState.Fee.InInputCurrency.String() != "0.0000192 BTC" {
		t.Fatalf("expected fee to match, got %v", confirmState.Fee.InInputCurrency)
	}
	if confirmState.Total.InInputCurrency.String() != "0.0001192 BTC" {
		t.Fatalf("expected total to match, got %v", confirmState.Total.InInputCurrency)
	}
}

//goland:noinspection GoUnhandledErrorResult
func TestLightningInvoiceWithAmountAndDescription(t *testing.T) {

	listener := newTestListener()
	startState := NewOperationFlow(listener)

	invoice, err := libwallet.ParseInvoice("lnbcrt100u1psjg8k6pp5n895ngj22v4dczwd8jrvvq76qvur2642y29m6x2faq0rgle2zwwsdq2vehk7cnpwgcqzpgsp5x9yeys2j294q402ewq2kfcas6wn63mk5q86ehe79plljzfwhr69s9qyyssq4exlg7ly068zc8dfh6ls5r69x0pmvdy9la70hw2vqwz9p2g4p5fyxr0hlkzrfnkmlx3kjrlecedatk96zuzs8a3cj48qg7vne6zp5ygpgzwd2x", libwallet.Regtest())
	if err != nil {
		panic(err)
	}

	startState.ResolveInvoice(invoice, libwallet.Regtest())

	resolveState := listener.next().(*ResolveState)

	context := createContext()
	context.SubmarineSwap = &SubmarineSwap{
		Fees: &SwapFees{
			RoutingFeeInSat:     0,
			DebtType:            DebtTypeNone,
			DebtAmountInSat:     0,
			OutputAmountInSat:   10000,
			OutputPaddingInSat:  0,
			ConfirmationsNeeded: 0,
		},
	}

	resolveState.setContextWithTime(context, time.Unix(1629475653, 0))

	validateState := listener.next().(*ValidateLightningState)
	validateState.Continue()

	confirmState := listener.next().(*ConfirmLightningState)

	if confirmState.Note != "foobar" {
		t.Fatalf("expected note to match input, got '%v'", confirmState.Note)
	}
	if confirmState.Amount.InInputCurrency.String() != "0.0001 BTC" {
		t.Fatalf("expected amount to match resolved URI, got %v", confirmState.Amount.InInputCurrency)
	}
	if confirmState.Fee.InInputCurrency.String() != "0.0000192 BTC" {
		t.Fatalf("expected fee to match, got %v", confirmState.Fee.InInputCurrency)
	}
	if confirmState.Total.InInputCurrency.String() != "0.0001192 BTC" {
		t.Fatalf("expected total to match, got %v", confirmState.Total.InInputCurrency)
	}
}

//goland:noinspection GoUnhandledErrorResult
func TestLightningAmountlessInvoice(t *testing.T) {

	listener := newTestListener()
	startState := NewOperationFlow(listener)

	invoice, err := libwallet.ParseInvoice("lnbcrt1ps3l7zlpp5ngv7sl4wrjalma9navd0w9956pu0tcqwrltcnnzz83eeyk4rszxqdqqcqzpgrzjq2eawnq2ywdmcpe56nk02tfamgfmsn0acp0zcn8z8cr0djkgpslr5qqpkgqqqqgqqqqqqqlgqqqqqqgq9qrzjq2eawnq2ywdmcpe56nk02tfamgfmsn0acp0zcn8z8cr0djkgpslr5qqpkgqqqqsqqqqqqqlgqqqqqqgq9qsp5luyagw4mtcq735je8ldukhlkg063cxzycjhpz2x2hjfq2mgk5xns9qyyssq7ydzdwyl7yr6ldpzqjjspmgrevw4lxt4jwfy3cxm7we20wveqq8p8khjxuq9u3v953e7t9r8ysfzx5r874vu3nd7w5yx5eqfxu0tevspgxr607", libwallet.Regtest())
	if err != nil {
		panic(err)
	}

	startState.ResolveInvoice(invoice, libwallet.Regtest())

	resolveState := listener.next().(*ResolveState)

	context := createContext()
	context.SubmarineSwap = &SubmarineSwap{
		BestRouteFees: []*BestRouteFees{
			{
				MaxCapacity:              100_000,
				FeeProportionalMillionth: 1,
				FeeBase:                  1_000,
			},
		},
		FundingOutputPolicies: &FundingOutputPolicies{
			MaximumDebtInSat:       0,
			PotentialCollectInSat:  0,
			MaxAmountInSatFor0Conf: 25_000,
		},
	}

	resolveState.setContextWithTime(context, time.Unix(1629485164, 0))

	enterAmountState := listener.next().(*EnterAmountState)
	enterAmountState.EnterAmount(NewMonetaryAmountFromSatoshis(10_000), false)

	validateState := listener.next().(*ValidateLightningState)
	validateState.Continue()

	enterDescriptionState := listener.next().(*EnterDescriptionState)
	enterDescriptionState.EnterDescription("foo")

	confirmState := listener.next().(*ConfirmLightningState)

	if confirmState.Note != "foo" {
		t.Fatalf("expected note to match input, got '%v'", confirmState.Note)
	}
	if confirmState.Amount.InInputCurrency.String() != "0.0001 BTC" {
		t.Fatalf("expected amount to match resolved URI, got %v", confirmState.Amount.InInputCurrency)
	}
	if confirmState.Fee.InInputCurrency.String() != "0.0000292 BTC" {
		t.Fatalf("expected fee to match, got %v", confirmState.Fee.InInputCurrency)
	}
	if confirmState.Total.InInputCurrency.String() != "0.0001292 BTC" {
		t.Fatalf("expected total to match, got %v", confirmState.Total.InInputCurrency)
	}
	if confirmState.SwapInfo.IsOneConf {
		t.Fatalf("expected swap to be 0 conf")
	}

	// Test a bug where the one conf flag was set after back
	confirmState.Back()
	enterDescriptionState = listener.next().(*EnterDescriptionState)
	if enterDescriptionState.SwapInfo.IsOneConf {
		t.Fatalf("expected swap to be 0 conf")
	}
}

//goland:noinspection GoUnhandledErrorResult
func TestInvoiceOneConf(t *testing.T) {

	listener := newTestListener()
	startState := NewOperationFlow(listener)

	invoice, err := libwallet.ParseInvoice("lnbcrt1ps3l7zlpp5ngv7sl4wrjalma9navd0w9956pu0tcqwrltcnnzz83eeyk4rszxqdqqcqzpgrzjq2eawnq2ywdmcpe56nk02tfamgfmsn0acp0zcn8z8cr0djkgpslr5qqpkgqqqqgqqqqqqqlgqqqqqqgq9qrzjq2eawnq2ywdmcpe56nk02tfamgfmsn0acp0zcn8z8cr0djkgpslr5qqpkgqqqqsqqqqqqqlgqqqqqqgq9qsp5luyagw4mtcq735je8ldukhlkg063cxzycjhpz2x2hjfq2mgk5xns9qyyssq7ydzdwyl7yr6ldpzqjjspmgrevw4lxt4jwfy3cxm7we20wveqq8p8khjxuq9u3v953e7t9r8ysfzx5r874vu3nd7w5yx5eqfxu0tevspgxr607", libwallet.Regtest())
	if err != nil {
		panic(err)
	}

	startState.ResolveInvoice(invoice, libwallet.Regtest())

	resolveState := listener.next().(*ResolveState)

	context := createContext()
	context.SubmarineSwap = &SubmarineSwap{
		BestRouteFees: []*BestRouteFees{
			{
				MaxCapacity:              100000,
				FeeProportionalMillionth: 1,
				FeeBase:                  1000,
			},
		},
		FundingOutputPolicies: &FundingOutputPolicies{
			MaximumDebtInSat:       0,
			PotentialCollectInSat:  0,
			MaxAmountInSatFor0Conf: 0,
		},
	}

	resolveState.setContextWithTime(context, time.Unix(1629485164, 0))

	enterAmountState := listener.next().(*EnterAmountState)
	enterAmountState.EnterAmount(NewMonetaryAmountFromSatoshis(10000), false)

	validateState := listener.next().(*ValidateLightningState)
	validateState.Continue()

	enterDescriptionState := listener.next().(*EnterDescriptionState)
	enterDescriptionState.EnterDescription("foo")
	if !enterDescriptionState.SwapInfo.IsOneConf {
		t.Fatalf("expected swap to be 1 conf")
	}

	confirmState := listener.next().(*ConfirmLightningState)

	if confirmState.Note != "foo" {
		t.Fatalf("expected note to match input, got '%v'", confirmState.Note)
	}
	if confirmState.Amount.InInputCurrency.String() != "0.0001 BTC" {
		t.Fatalf("expected amount to match resolved URI, got %v", confirmState.Amount.InInputCurrency)
	}
	if confirmState.Fee.InInputCurrency.String() != "0.00097 BTC" {
		t.Fatalf("expected fee to match, got %v", confirmState.Fee.InInputCurrency)
	}
	if confirmState.Total.InInputCurrency.String() != "0.00107 BTC" {
		t.Fatalf("expected total to match, got %v", confirmState.Total.InInputCurrency)
	}
	if !confirmState.SwapInfo.IsOneConf {
		t.Fatalf("expected swap to be 1 conf")
	}
}

//goland:noinspection GoUnhandledErrorResult
func TestAmountConversion(t *testing.T) {

	// This test repros a bug where we had:
	// * Primary currency BTC
	// * Fiat input
	// Then, for amount/total the amount in sat and in primary currency differed
	// in 1 sat.

	listener := newTestListener()
	startState := NewOperationFlow(listener)

	invoice, err := libwallet.ParseInvoice("lnbcrt1ps3l7zlpp5ngv7sl4wrjalma9navd0w9956pu0tcqwrltcnnzz83eeyk4rszxqdqqcqzpgrzjq2eawnq2ywdmcpe56nk02tfamgfmsn0acp0zcn8z8cr0djkgpslr5qqpkgqqqqgqqqqqqqlgqqqqqqgq9qrzjq2eawnq2ywdmcpe56nk02tfamgfmsn0acp0zcn8z8cr0djkgpslr5qqpkgqqqqsqqqqqqqlgqqqqqqgq9qsp5luyagw4mtcq735je8ldukhlkg063cxzycjhpz2x2hjfq2mgk5xns9qyyssq7ydzdwyl7yr6ldpzqjjspmgrevw4lxt4jwfy3cxm7we20wveqq8p8khjxuq9u3v953e7t9r8ysfzx5r874vu3nd7w5yx5eqfxu0tevspgxr607", libwallet.Regtest())
	if err != nil {
		panic(err)
	}

	startState.ResolveInvoice(invoice, libwallet.Regtest())

	resolveState := listener.next().(*ResolveState)

	context := createContext()
	context.SubmarineSwap = &SubmarineSwap{
		BestRouteFees: []*BestRouteFees{
			{
				MaxCapacity:              100000,
				FeeProportionalMillionth: 1,
				FeeBase:                  1000,
			},
		},
		FundingOutputPolicies: &FundingOutputPolicies{
			MaximumDebtInSat:       0,
			PotentialCollectInSat:  0,
			MaxAmountInSatFor0Conf: 0,
		},
	}

	resolveState.setContextWithTime(context, time.Unix(1629485164, 0))

	enterAmountState := listener.next().(*EnterAmountState)
	enterAmountState.EnterAmount(NewMonetaryAmountFromFiat("2500", "ARS"), false)

	validateState := listener.next().(*ValidateLightningState)
	validateState.Continue()

	enterDescriptionState := listener.next().(*EnterDescriptionState)
	enterDescriptionState.EnterDescription("foo")

	confirmState := listener.next().(*ConfirmLightningState)
	btcToSats := decimal.NewFromInt(100_000_000)

	inPrimary := confirmState.Amount.InPrimaryCurrency.Value
	// This emulates the conversions app make (and the Right Way™ to do it)
	primaryInSats := inPrimary.Mul(btcToSats).RoundBank(0).IntPart()
	if primaryInSats != confirmState.Amount.InSat {
		t.Fatalf(
			"expected amount in primary to match sats: %v != %v",
			primaryInSats,
			confirmState.Amount.InSat,
		)
	}
}

//goland:noinspection GoUnhandledErrorResult
func TestInvoiceUnpayable(t *testing.T) {

	listener := newTestListener()
	startState := NewOperationFlow(listener)

	invoice, err := libwallet.ParseInvoice("lnbcrt1ps3l7zlpp5ngv7sl4wrjalma9navd0w9956pu0tcqwrltcnnzz83eeyk4rszxqdqqcqzpgrzjq2eawnq2ywdmcpe56nk02tfamgfmsn0acp0zcn8z8cr0djkgpslr5qqpkgqqqqgqqqqqqqlgqqqqqqgq9qrzjq2eawnq2ywdmcpe56nk02tfamgfmsn0acp0zcn8z8cr0djkgpslr5qqpkgqqqqsqqqqqqqlgqqqqqqgq9qsp5luyagw4mtcq735je8ldukhlkg063cxzycjhpz2x2hjfq2mgk5xns9qyyssq7ydzdwyl7yr6ldpzqjjspmgrevw4lxt4jwfy3cxm7we20wveqq8p8khjxuq9u3v953e7t9r8ysfzx5r874vu3nd7w5yx5eqfxu0tevspgxr607", libwallet.Regtest())
	if err != nil {
		panic(err)
	}

	startState.ResolveInvoice(invoice, libwallet.Regtest())

	resolveState := listener.next().(*ResolveState)

	context := createContext()
	context.SubmarineSwap = &SubmarineSwap{
		BestRouteFees: []*BestRouteFees{
			{
				MaxCapacity:              100000,
				FeeProportionalMillionth: 1,
				FeeBase:                  1000,
			},
		},
		FundingOutputPolicies: &FundingOutputPolicies{
			MaximumDebtInSat:       0,
			PotentialCollectInSat:  0,
			MaxAmountInSatFor0Conf: 0,
		},
	}

	resolveState.setContextWithTime(context, time.Unix(1629485164, 0))

	enterAmountState := listener.next().(*EnterAmountState)
	enterAmountState.EnterAmount(NewMonetaryAmountFromSatoshis(99_999_999), false)

	validateState := listener.next().(*ValidateLightningState)
	validateState.Continue()

	errorState := listener.next().(*BalanceErrorState)
	if errorState.Error != OperationErrorUnpayable {
		t.Fatalf("Expected error to be OperationErrorUnpayable: %v", errorState.Error)
	}
	if errorState.Balance.String() != "1 BTC" {
		t.Fatalf("Expected balance to be 1 BTC: %v", errorState.Balance.String())
	}
	if errorState.TotalAmount.String() != "1.00097098 BTC" {
		t.Fatalf("Expected total amount to be 1 BTC: %v", errorState.TotalAmount.String())
	}

}

//goland:noinspection GoUnhandledErrorResult
func TestInvoiceLend(t *testing.T) {

	listener := newTestListener()
	startState := NewOperationFlow(listener)

	invoice, err := libwallet.ParseInvoice("lnbcrt1u1ps5ma8app59ujxjvtj8x34fyd7u7tghuq44dphjth8nqmkzeklg882y9ghjmvqdqqcqzpgsp5stzjqktxfh02dfz8tucfnh6rl3z87ctl2dumr40elhmrskhx5zlq9qyyssqnp4uhukcgxx6l0p5elppz5xc7a97n0hxvfm6lgr6ze06wqc2dnx95pet3vlalc9rqz20lu45y8sqg3n6fm6tqsftvzqp4l2zsrs0ndgpk2fyv5", libwallet.Regtest())
	if err != nil {
		panic(err)
	}

	startState.ResolveInvoice(invoice, libwallet.Regtest())

	resolveState := listener.next().(*ResolveState)

	context := createContext()
	context.SubmarineSwap = &SubmarineSwap{
		Fees: &SwapFees{
			RoutingFeeInSat:     0,
			DebtType:            DebtTypeLend,
			DebtAmountInSat:     100,
			OutputAmountInSat:   546,
			OutputPaddingInSat:  446,
			ConfirmationsNeeded: 0,
		},
	}

	resolveState.setContextWithTime(context, time.Unix(1629475653, 0))

	validateState := listener.next().(*ValidateLightningState)
	validateState.Continue()

	enterDescriptionState := listener.next().(*EnterDescriptionState)
	enterDescriptionState.EnterDescription("foo")

	confirmState := listener.next().(*ConfirmLightningState)

	if confirmState.Note != "foo" {
		t.Fatalf("expected note to match input, got '%v'", confirmState.Note)
	}
	if confirmState.Amount.InInputCurrency.String() != "0.000001 BTC" {
		t.Fatalf("expected amount to match resolved URI, got %v", confirmState.Amount.InInputCurrency)
	}
	if confirmState.Fee.InInputCurrency.String() != "0 BTC" {
		t.Fatalf("expected fee to match, got %v", confirmState.Fee.InInputCurrency)
	}
	if confirmState.Total.InInputCurrency.String() != "0.000001 BTC" {
		t.Fatalf("expected total to match, got %v", confirmState.Total.InInputCurrency)
	}
	if confirmState.SwapInfo.SwapFees.DebtType != DebtTypeLend {
		t.Fatalf("Expected debt type to be lend: %v", confirmState.SwapInfo.SwapFees.DebtType)
	}
	if confirmState.SwapInfo.SwapFees.DebtAmountInSat != 100 {
		t.Fatalf("Expected debt amount to be 100 sats: %v", confirmState.SwapInfo.SwapFees.DebtAmountInSat)
	}
}

//goland:noinspection GoUnhandledErrorResult
func TestAmountInfo_Mutating(t *testing.T) {
	amountInfo := &AmountInfo{
		TakeFeeFromAmount:     false,
		FeeRateInSatsPerVByte: 0,
	}

	mutated := amountInfo.mutating(func(info *AmountInfo) {
		info.TakeFeeFromAmount = true
	})

	if amountInfo.TakeFeeFromAmount {
		t.Fatalf("Expected original to stay the same")
	}
	if !mutated.TakeFeeFromAmount {
		t.Fatalf("Mutated should be mutated")
	}
}

//goland:noinspection GoUnhandledErrorResult
func TestOnChainTFFAWithDebtFeeNeedsChangeBecauseOutputAmountLowerThanDust(t *testing.T) {

	listener := newTestListener()
	startState := NewOperationFlow(listener)

	startState.Resolve("bitcoin:bcrt1qj35fkq34xend9w0ssthn432vl9pxxsuy0epzlu", libwallet.Regtest())

	context := createContext()

	nts := &NextTransactionSize{}
	nts.AddSizeForAmount(&SizeForAmount{
		AmountInSat: 5338,
		SizeInVByte: 172,
	})
	nts.ExpectedDebtInSat = 4353
	context.NextTransactionSize = nts

	context.FeeWindow.PutTargetedFees(100, 1.0)

	resolveState := listener.next().(*ResolveState)
	resolveState.SetContext(context)

	enterAmountState := listener.next().(*EnterAmountState)
	enterAmountState.EnterAmount(NewMonetaryAmountFromSatoshis(985), true)

	validateState := listener.next().(*ValidateState)
	validateState.Continue()

	enterDescriptionState := listener.next().(*EnterDescriptionState)
	enterDescriptionState.EnterDescription("bar")

	// Amount is not payable with fastes/highest fee, but it is with min fee (1 sat/vbyte)
	if enterDescriptionState.Amount.InInputCurrency.String() != "0 BTC" {
		t.Fatalf("expected amount to match input, got %v", enterDescriptionState.Amount.InInputCurrency)
	}

	confirmState := listener.next().(*ConfirmState)

	if confirmState.Note != "bar" {
		t.Fatalf("expected note to match input, got '%v'", confirmState.Note)
	}
	if confirmState.Amount.InInputCurrency.String() != "0 BTC" {
		t.Fatalf("expected amount to match input, got %v", confirmState.Amount.InInputCurrency)
	}
	if confirmState.Fee.InInputCurrency.String() != "0.000688 BTC" {
		t.Fatalf("expected fee to match, got %v", confirmState.Fee.InInputCurrency)
	}
	if confirmState.Total.InInputCurrency.String() != "0.000688 BTC" {
		t.Fatalf("expected total to match, got %v", confirmState.Total.InInputCurrency)
	}
	if confirmState.FeeNeedsChange != true {
		t.Fatalf("expected feedsNeedsChange to be true, got %v", confirmState.FeeNeedsChange)
	}

	confirmState.OpenFeeEditor()
	editFeeState := listener.next().(*EditFeeState)

	editFeeState.SetFeeRate(1)

	validateState = listener.next().(*ValidateState)
	validateState.Continue()

	confirmState = listener.next().(*ConfirmState)

	if confirmState.Note != "bar" {
		t.Fatalf("expected note to match input, got '%v'", confirmState.Note)
	}
	if confirmState.Amount.InInputCurrency.String() != "0.00000813 BTC" {
		t.Fatalf("expected amount to match input, got %v", confirmState.Amount.InInputCurrency)
	}
	if confirmState.Fee.InInputCurrency.String() != "0.00000172 BTC" {
		t.Fatalf("expected fee to match, got %v", confirmState.Fee.InInputCurrency)
	}
	if confirmState.Total.InInputCurrency.String() != "0.00000985 BTC" {
		t.Fatalf("expected total to match, got %v", confirmState.Total.InInputCurrency)
	}
	if confirmState.FeeNeedsChange != false {
		t.Fatalf("expected feedsNeedsChange to be false, got %v", confirmState.FeeNeedsChange)
	}
}
