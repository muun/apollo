package io.muun.apollo.presentation.ui.new_operation

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import io.muun.apollo.data.external.Globals
import io.muun.apollo.data.serialization.SerializationUtils
import io.muun.apollo.domain.action.base.CombineLatestAsyncAction
import io.muun.apollo.domain.action.operation.ResolveOperationUriAction
import io.muun.apollo.domain.action.operation.SubmitPaymentAction
import io.muun.apollo.domain.action.realtime.FetchRealTimeDataAction
import io.muun.apollo.domain.action.realtime.PreloadFeeDataAction
import io.muun.apollo.domain.analytics.AnalyticsEvent
import io.muun.apollo.domain.analytics.AnalyticsEvent.E_NEW_OP_ACTION_TYPE
import io.muun.apollo.domain.analytics.AnalyticsEvent.E_NEW_OP_COMPLETED
import io.muun.apollo.domain.analytics.AnalyticsEvent.E_NEW_OP_SUBMITTED
import io.muun.apollo.domain.analytics.AnalyticsEvent.E_NEW_OP_TYPE.Companion.fromModel
import io.muun.apollo.domain.analytics.AnalyticsEvent.S_NEW_OP_ORIGIN.Companion.fromModel
import io.muun.apollo.domain.analytics.NewOperationOrigin
import io.muun.apollo.domain.errors.BugDetected
import io.muun.apollo.domain.errors.UserFacingError
import io.muun.apollo.domain.errors.newop.AmountTooSmallError
import io.muun.apollo.domain.errors.newop.CyclicalSwapError
import io.muun.apollo.domain.errors.newop.ExchangeRateWindowTooOldError
import io.muun.apollo.domain.errors.newop.InsufficientFundsError
import io.muun.apollo.domain.errors.newop.InvalidInvoiceException
import io.muun.apollo.domain.errors.newop.InvalidSwapException
import io.muun.apollo.domain.errors.newop.InvoiceAlreadyUsedException
import io.muun.apollo.domain.errors.newop.InvoiceExpiredException
import io.muun.apollo.domain.errors.newop.InvoiceExpiresTooSoonException
import io.muun.apollo.domain.errors.newop.InvoiceMissingAmountException
import io.muun.apollo.domain.errors.newop.NoPaymentRouteException
import io.muun.apollo.domain.errors.newop.SwapFailedException
import io.muun.apollo.domain.errors.newop.UnreachableNodeException
import io.muun.apollo.domain.libwallet.Invoice.parseInvoice
import io.muun.apollo.domain.libwallet.toLibwallet
import io.muun.apollo.domain.model.BitcoinAmount
import io.muun.apollo.domain.model.Contact
import io.muun.apollo.domain.model.Operation
import io.muun.apollo.domain.model.OperationUri
import io.muun.apollo.domain.model.PaymentContext
import io.muun.apollo.domain.model.PaymentRequest
import io.muun.apollo.domain.model.PaymentRequest.Type
import io.muun.apollo.domain.model.PreparedPayment
import io.muun.apollo.domain.model.SubmarineSwap
import io.muun.apollo.domain.model.feebump.FeeBumpRefreshPolicy
import io.muun.apollo.domain.selector.BitcoinUnitSelector
import io.muun.apollo.domain.selector.ExchangeRateSelector
import io.muun.apollo.domain.selector.FeatureSelector
import io.muun.apollo.domain.selector.PaymentContextSelector
import io.muun.apollo.presentation.ui.base.BasePresenter
import io.muun.apollo.presentation.ui.base.di.PerActivity
import io.muun.apollo.presentation.ui.fragments.manual_fee.ManualFeeParentPresenter
import io.muun.apollo.presentation.ui.fragments.new_op_error.NewOperationErrorParentPresenter
import io.muun.apollo.presentation.ui.fragments.new_op_error.NewOperationErrorParentPresenter.ErrorMetadata
import io.muun.apollo.presentation.ui.fragments.recommended_fee.RecommendedFeeParentPresenter
import io.muun.apollo.presentation.ui.new_operation.NewOperationView.Receiver
import io.muun.common.Rules
import io.muun.common.utils.Preconditions
import newop.AbortState
import newop.BalanceErrorState
import newop.ConfirmLightningState
import newop.ConfirmState
import newop.EditFeeState
import newop.EnterAmountState
import newop.EnterDescriptionState
import newop.ErrorState
import newop.Newop
import newop.ResolveState
import newop.Resolved
import newop.StartState
import newop.State
import newop.ValidateLightningState
import newop.ValidateState
import rx.Observable
import timber.log.Timber
import java.util.Locale
import javax.inject.Inject
import javax.money.MonetaryAmount

@PerActivity
class NewOperationPresenter @Inject constructor(
    private val fetchRealTimeData: FetchRealTimeDataAction,
    private val paymentContextSel: PaymentContextSelector,
    private val exchangeRateSel: ExchangeRateSelector,
    private val bitcoinUnitSel: BitcoinUnitSelector,
    private val featureSel: FeatureSelector,
    private val submitPayment: SubmitPaymentAction,
    private val resolveOperationUri: ResolveOperationUriAction,
    private val preloadFeeData: PreloadFeeDataAction,
) : BasePresenter<NewOperationView>(),
    RecommendedFeeParentPresenter,
    ManualFeeParentPresenter,
    NewOperationErrorParentPresenter {

    private val networkParams = Globals.INSTANCE.network

    companion object {

        private fun getEventType(payReqType: Type): AnalyticsEvent.E_NEW_OP_TYPE {
            return fromModel(payReqType)
        }

        private fun getEventOrigin(origin: NewOperationOrigin): AnalyticsEvent.S_NEW_OP_ORIGIN {
            return fromModel(origin)
        }
    }

    private val stateMachine = NewOperationStateMachine()

    private lateinit var origin: NewOperationOrigin

    private var confirmationInProgress: Boolean = false

    private var unknownError: Throwable? = null

    private var submarineSwap: SubmarineSwap? = null

    private var contact: Contact? = null

    private val receiver by lazy {
        object : Receiver {

            override val swap: SubmarineSwap?
                get() = this@NewOperationPresenter.submarineSwap

            override val contact: Contact?
                get() = this@NewOperationPresenter.contact
        }
    }

    private val has2fa by lazy {
        featureSel.hasSecurityCardEnabled()
    }

    // Ugly AF. But also easy and quick, best effort to store data for rich error reporting.
    // TODO do this the RIGHT way
    private lateinit var confirmStateViewModel: ConfirmStateViewModel

    fun startForBitcoinUri(uri: String, operationUri: OperationUri, origin: NewOperationOrigin) {

        val isActivityRecreation = handleStart(uri, origin)

        if (isActivityRecreation) {
            return
        }

        // Since LN payments are handled in startForInvoice() (according to OperationUri#isLn()), if
        // the operationUri has a ln invoice here it means we are dealing with a "Unified QR" uri.
        if (operationUri.lnInvoice.isPresent) {
            reportNewOpAction(E_NEW_OP_ACTION_TYPE.START_FOR_UNIFIED_QR)

        } else {
            reportNewOpAction(E_NEW_OP_ACTION_TYPE.START_FOR_BITCOIN_URI)
        }

        stateMachine.withState { state: StartState ->
            state.resolve(uri, networkParams.toLibwallet())
        }

        if (operationUri.isAsync) {
            // We're just using it to show loading state "earlier"
            view.goToResolvingState()
        }
    }

    fun startForInvoice(invoice: String, newOpOrigin: NewOperationOrigin) {

        val isActivityRecreation = handleStart(invoice, newOpOrigin)

        if (isActivityRecreation) {
            return
        }

        reportNewOpAction(E_NEW_OP_ACTION_TYPE.START_FOR_INVOICE)
        stateMachine.withState { state: StartState ->
            state.resolveInvoice(parseInvoice(networkParams, invoice), networkParams.toLibwallet())
        }
    }

    override fun setUp(arguments: Bundle?) {
        super.setUp(arguments)

        // Set up SubmitPaymentAction. Needs to be set up first (e.g first async action to be
        // subscribed), to avoid its ActionState (will initially fire an EMPTY action state) messing
        // with global handleStates handleLoading action/param.
        submitPayment
            .state
            .compose(handleStates(view::setLoading, this::handleError))
            .doOnNext { operation: Operation ->
                onSubmitPaymentSuccess(operation)
            }
            .let(this::subscribeTo)

        // Set up RealTimeData and PaymentContext
        val basicActions = CombineLatestAsyncAction(
            fetchRealTimeData.state,
            resolveOperationUri.state
        )

        // This data is required to set the initial context (in Resolve State).
        // To prevent bugs when going and coming back from background,
        // subscribe only when necessary.
        val isInResolveState = stateMachine.value() is ResolveState
        if (isInResolveState) {
            CombineLatestAsyncAction(basicActions.getState(), preloadFeeData.state)
                .getState()
                .compose(handleStates(view::setLoading, this::handleError))
                .doOnNext { (basicActionsPair, _) ->
                    // Once we've updated exchange and fee rates, we can proceed with our preparations.
                    // This is especially important if we landed here after the user clicked an external
                    // link, since she skipped the home screen and didn't automatically fetch RTD.

                    // Both realTimeData and realtimeFees we just call the actions for its side-effects
                    // (aka refreshing the data in local storage).
                    val paymentRequest = basicActionsPair!!.second

                    // Note that RTD fetching is instantaneous if it was already up to date.
                    paymentContextSel.watch()
                        .first()
                        .doOnNext { newPayCtx: PaymentContext ->
                            onPaymentContextChanged(newPayCtx, paymentRequest)
                        }
                        .let(this::subscribeTo)

                }
                .let(this::subscribeTo)
        }

        subscribeTo(stateMachine.asObservable()) { state ->
            onStateChanged(state)
        }
    }

    private fun onSubmitPaymentSuccess(op: Operation) {

        if (submarineSwap == null) {
            val state = stateMachine.value()!! as ConfirmState
            reportFinished(op.id!!, ConfirmStateViewModel.fromConfirmState(state))

        } else {
            val state = stateMachine.value()!! as ConfirmLightningState
            reportFinished(op.id!!, ConfirmStateViewModel.fromConfirmLightningState(state))
        }

        navigator.navigateToHome(context, op)
        view.finishActivity()
    }

    override fun watchEditFeeState(): Observable<EditFeeState> =
        stateMachine.asObservable()
            .filter { it is EditFeeState }
            .map { it as EditFeeState }

    override fun confirmFee(selectedFeeRateInVBytes: Double) {
        view.goToConfirmedFee()
        reportNewOpAction(E_NEW_OP_ACTION_TYPE.CONFIRM_FEE)
        if (selectedFeeRateInVBytes >= Rules.toSatsPerVbyte(Rules.OP_MINIMUM_FEE_RATE)) {
            stateMachine.withState { state: EditFeeState ->
                state.setFeeRate(selectedFeeRateInVBytes)
            }
        } else {
            Timber.e(BugDetected("Invalid fee rate selected: $selectedFeeRateInVBytes"))
        }
    }

    override fun editFeeManually() {
        view.goToEditFeeManually()
    }

    override fun goHomeInDefeat() {
        view.finishActivity()
    }

    override fun getErrorMetadata(): ErrorMetadata =
        ErrorMetadata(stateMachine.value() as? ErrorState, unknownError)

    override fun watchBalanceErrorState(): Observable<BalanceErrorState> =
        stateMachine.asObservable()
            .filter { it is BalanceErrorState }
            .map { it as BalanceErrorState }

    // Special Error handling for error PRIOR to handleStart() (e.g in Activity#onCreate)
    // TODO: this should probably be handled better
    fun handleError(error: Throwable, newOpOrigin: NewOperationOrigin) {
        if (!::origin.isInitialized) {
            origin = newOpOrigin
        }
        handleError(error)
    }

    override fun handleError(error: Throwable) {
        confirmationInProgress = false
        view.setLoading(false)
        super.handleError(error)
    }

    override fun handleNonFatalError(error: Throwable): Boolean {
        when (error) {
            is UnreachableNodeException ->
                handleNewOpError(NewOperationErrorType.INVOICE_UNREACHABLE_NODE, error)

            is NoPaymentRouteException ->
                handleNewOpError(NewOperationErrorType.INVOICE_NO_ROUTE, error)

            // SwapFailed is currently thrown due to abnormal mempool situation where outgoing swaps
            // can't be performed correctly. We choose to handle it like a no route payment.
            is SwapFailedException ->
                handleNewOpError(NewOperationErrorType.SWAP_FAILED, error)

            is InvoiceExpiresTooSoonException ->
                handleNewOpError(NewOperationErrorType.INVOICE_WILL_EXPIRE_SOON, error)

            is InvoiceExpiredException ->
                handleNewOpError(NewOperationErrorType.INVOICE_EXPIRED, error)

            is InvoiceAlreadyUsedException ->
                handleNewOpError(NewOperationErrorType.INVOICE_ALREADY_USED, error)

            is InvoiceMissingAmountException ->
                handleNewOpError(NewOperationErrorType.INVOICE_MISSING_AMOUNT, error)

            is InvalidInvoiceException ->
                handleNewOpError(NewOperationErrorType.INVALID_INVOICE, error)

            is InvalidSwapException ->
                handleNewOpError(NewOperationErrorType.INVALID_SWAP, error)

            is InsufficientFundsError ->
                handleNewOpError(NewOperationErrorType.INSUFFICIENT_FUNDS, error)

            is ExchangeRateWindowTooOldError ->
                handleNewOpError(NewOperationErrorType.EXCHANGE_RATE_WINDOW_TOO_OLD, error)

            is CyclicalSwapError ->
                handleNewOpError(NewOperationErrorType.CYCLICAL_SWAP, error)

            // This error should only reach us if the user scanned a QR with an invalid amount (eg
            // DUST), and is not allowed to change it. There's nothing we can do.
            is AmountTooSmallError ->
                handleNewOpError(NewOperationErrorType.AMOUNT_TOO_SMALL, error)

            is UserFacingError -> {
                unknownError = error
                Timber.e(error)
                handleNewOpError(NewOperationErrorType.GENERIC, error)
            }

            else ->
                return super.handleNonFatalError(error)
        }
        return true
    }

    override fun handleUnknownError(error: Throwable) {
        unknownError = error
        Timber.e(error)
        handleNewOpError(NewOperationErrorType.GENERIC, error)
    }

    fun updateAmount(
        oldAmount: MonetaryAmount,
        newAmount: MonetaryAmount,
        state: EnterAmountState,
    ) {

        // This is our way of detecting a currency change. Since the feature is abstracted into
        // MuunAmountInput and the exposed API reports the new amount.
        if (newAmount.currency.currencyCode != state.amount.inInputCurrency.currency) {
            state.changeCurrencyWithAmount(newAmount.currency.currencyCode, oldAmount.toLibwallet())
        }

        if (!state.partialValidate(newAmount.toLibwallet())) {
            view.setAmountInputError()
        }
    }

    fun confirmAmount(value: MonetaryAmount, takeFreeFromAmount: Boolean) {
        reportNewOpAction(E_NEW_OP_ACTION_TYPE.CONFIRM_AMOUNT)
        stateMachine.withState { state: EnterAmountState ->
            state.enterAmount(value.toLibwallet(), takeFreeFromAmount)
        }
    }

    fun confirmUseAllFunds() {
        reportNewOpAction(E_NEW_OP_ACTION_TYPE.USE_ALL_FUNDS)
        stateMachine.withState { state: EnterAmountState ->
            state.enterAmount(state.totalBalance.inInputCurrency, true)
        }
    }

    fun confirmDescription(description: String) {
        reportNewOpAction(E_NEW_OP_ACTION_TYPE.CONFIRM_DESCRIPTION)
        stateMachine.withState { state: EnterDescriptionState ->
            state.enterDescription(description)
        }
    }

    fun editFee() {

        // If payment submission is in progress, avoid further state machine changes until it ends
        if (confirmationInProgress) {
            return
        }

        // Using this getter + check instead of withState to avoid quick double clicks to crash app
        val state = stateMachine.value()!!
        if (state is ConfirmState) {
            state.openFeeEditor()
        }
    }

    fun handleConfirmClick(nfcReaderLauncher: ActivityResultLauncher<Intent>) {
        if (featureSel.hasSecurityCardEnabled()) {
            navigator.navigateToNfcReaderActivityForResult(context, nfcReaderLauncher)

        } else {
            handleConfirmOperation()
        }
    }

    /**
     * Proceeds to finish the process either by sending an ON-CHAIN or OFF-CHAIN operation.
     */
    private fun handleConfirmOperation() {
        if (receiver.swap == null) {
            confirmOperation()
        } else {
            confirmSwapOperation()
        }
    }

    internal fun handleSecurityCard2faResult(result: ActivityResult?) {
        if (result?.resultCode == Activity.RESULT_OK) {
            handleConfirmOperation()
        }
    }

    /**
     * Finishes the process by sending the ON-CHAIN operation to Houston.
     */
    private fun confirmOperation() {
        confirmationInProgress = true

        reportNewOpAction(E_NEW_OP_ACTION_TYPE.CONFIRM_OPERATION)
        stateMachine.withState { state: ConfirmState ->
            submitOperation(ConfirmStateViewModel.fromConfirmState(state))
        }
    }

    /**
     * Finishes the process by sending the SWAP operation to Houston.
     */
    private fun confirmSwapOperation() {
        confirmationInProgress = true

        reportNewOpAction(E_NEW_OP_ACTION_TYPE.CONFIRM_SWAP_OPERATION)
        stateMachine.withState { state: ConfirmLightningState ->
            submitOperation(
                ConfirmStateViewModel.fromConfirmLightningState(state),
                submarineSwap!!.withAmountLessInfo(state.validated.swapInfo)
            )
        }
    }

    fun reportShowDestinationInfo() {
        analytics.report(
            AnalyticsEvent.S_MORE_INFO(AnalyticsEvent.S_MORE_INFO_TYPE.NEW_OP_DESTINATION)
        )
    }

    fun goBack() {
        if (confirmationInProgress) {
            return
        }

        reportNewOpAction(E_NEW_OP_ACTION_TYPE.BACK)
        stateMachine.withState { state: State ->
            when (state) {
                is EnterAmountState -> state.back()
                is EnterDescriptionState -> state.back()
                is ConfirmState -> state.back()
                is ConfirmLightningState -> state.back()
                is EditFeeState -> state.closeEditor()

                else -> Timber.e("Attempted back in state that doesn't support it:${state}")
            }
        }
    }

    fun cancelAbort() {
        reportNewOpAction(E_NEW_OP_ACTION_TYPE.CANCEL_ABORT)
        stateMachine.withState { state: AbortState ->
            state.cancel()
        }
    }

    fun finishAndGoHome() {
        reportNewOpAction(E_NEW_OP_ACTION_TYPE.ABORT)
        view.finishAndGoHome()
    }

    private fun reportNewOpAction(type: E_NEW_OP_ACTION_TYPE) {
        var params: Array<Pair<String, Any>> = arrayOf()
        if (submarineSwap != null) {
            params += ("swap_uuid" to submarineSwap!!.houstonUuid)
        }
        if (has2fa) {
            params += ("has_2fa" to has2fa.toString())
        }
        analytics.report(AnalyticsEvent.E_NEW_OP_ACTION(type, *params))

    }

    fun handleNewOpError(errorType: NewOperationErrorType, cause: Throwable? = null) {
        reportNewOpError(errorType, cause)
        view.showErrorScreen(errorType)
    }

    private fun reportNewOpError(errorType: NewOperationErrorType, cause: Throwable?) {
        var params: Array<Pair<String, Any>> = arrayOf()
        if (submarineSwap != null) {
            params += ("swap_uuid" to submarineSwap!!.houstonUuid)
        }
        if (has2fa) {
            params += ("has_2fa" to has2fa.toString())
        }

        if (::confirmStateViewModel.isInitialized) {
            params += opSubmittedMetadata(confirmStateViewModel)
        }

        analytics.report(
            AnalyticsEvent.S_NEW_OP_ERROR(
                origin.toAnalyticsEvent(),
                errorType.toAnalyticsEvent(),
                cause,
                *params
            )
        )
    }

    /**
     * Handle lnurl deeplinks that are now redirected to NewOperationActivity.
     * TODO: change this. Deeplinks should all go through a dispatcher activity (LauncherActivity)
     */
    fun handleLnUrl(lnurl: String?) {
        navigator.navigateToLnUrlWithdraw(context, lnurl)
    }

    /**
     * Handle common start initialization for both Bitcoin uris and LN invoices.
     * Returns whether state machine was already initiated or not (e.g whether this is an activity
     * recreation or not).
     */
    private fun handleStart(uri: String, newOpOrigin: NewOperationOrigin): Boolean {
        if (!::origin.isInitialized) {
            origin = newOpOrigin
        }

        fetchRealTimeData.run() // if it's already running (eg ran by home screen), no problem.

        // runIfDataIsInvalidated checks if the fee bump functions in Libwallet are outdated
        // (invalidated).
        // If the data is invalidated: make an API call to refresh it.
        // If the data is fresh: simply returns null to complete loading on this screen.
        preloadFeeData.runIfDataIsInvalidated(FeeBumpRefreshPolicy.NEW_OP_BLOCKINGLY)

        // This is still needed because we need to:
        // - resolveLnInvoice for submarine swaps TODO mv this to libwallet
        // - resolveMuunUri for P2P/Contacts legacy feature TODO refactor this?
        resolveOperationUri.run(OperationUri.fromString(uri), origin)

        view.setInitialBitcoinUnit(bitcoinUnitSel.get())

        return stateMachine.value() as? StartState == null
    }

    private fun onPaymentContextChanged(newPayCtx: PaymentContext, payReq: PaymentRequest?) {
        stateMachine.asObservable()
            .filter { it is ResolveState }
            .map { it as ResolveState }
            .doOnNext { state: ResolveState ->

                if (state.paymentIntent.getPaymentType() == Type.TO_LN_INVOICE) {
                    submarineSwap = payReq?.swap
                }
                if (state.paymentIntent.getPaymentType() == Type.TO_CONTACT) {
                    contact = payReq?.contact
                }

                // Fix exchangeRateWindow so all the flow (e.g selectCurrency screen) uses the same
                exchangeRateSel.fixWindow(newPayCtx.exchangeRateWindow)
                state.setContext(newPayCtx.toLibwallet(submarineSwap))
            }
            .let(this::subscribeTo)
    }

    private fun onStateChanged(state: State) {
        if (state.update == Newop.UpdateEmpty) {
            return // state machine is telling us there's no need to update anything (e.g back)
        }

        when (state) {
            is StartState -> {
            } // Do nothing
            is ResolveState -> handleResolveState(state)
            is EnterAmountState -> handleEnterAmountState(state)
            is EnterDescriptionState -> handleEnterDescriptionState(state)
            is ValidateState -> handleValidateState(state)
            is ValidateLightningState -> handleValidateLightningState(state)
            is ConfirmState -> handleConfirmState(state)
            is ConfirmLightningState -> handleConfirmLightningState(state)
            is EditFeeState -> handleEditFeeState()
            is ErrorState -> handleErrorState(state)
            is BalanceErrorState -> handleBalanceErrorState(state)
            is AbortState -> handleAbortState()

            else -> throw IllegalStateException("Unrecognized new operation state: $state")
        }
    }

    private fun handleResolveState(state: ResolveState) {
        analytics.report(
            AnalyticsEvent.S_NEW_OP_LOADING(
                *uncheckedConvert(opStartedMetadata(state.paymentIntent.getPaymentType())),
                "update" to state.update,
            )
        )
        view.goToResolvingState()
    }

    private fun handleEnterAmountState(state: EnterAmountState) {
        analytics.report(
            AnalyticsEvent.S_NEW_OP_AMOUNT(
                *uncheckedConvert(opStartedMetadata(state.resolved.paymentIntent.getPaymentType())),
                "update" to state.update
            )
        )
        view.goToEnterAmountState(state, receiver)
    }

    private fun handleEnterDescriptionState(state: EnterDescriptionState) {
        analytics.report(
            AnalyticsEvent.S_NEW_OP_DESCRIPTION(
                *uncheckedConvert(opStartedMetadata(state.resolved.paymentIntent.getPaymentType())),
                "update" to state.update
            )
        )
        view.goToEnterDescriptionState(state, receiver, bitcoinUnitSel.get())
    }

    private fun handleValidateState(state: ValidateState) {
        state.continue_()
    }

    private fun handleValidateLightningState(state: ValidateLightningState) {
        state.continue_()
    }

    private fun handleConfirmState(state: ConfirmState) {
        handleConfirmState(ConfirmStateViewModel.fromConfirmState(state))
    }

    private fun handleConfirmState(confirmStateViewModel: ConfirmStateViewModel) {
        this.confirmStateViewModel = confirmStateViewModel
        analytics.report(
            AnalyticsEvent.S_NEW_OP_CONFIRMATION(
                *uncheckedConvert(opSubmittedMetadata(confirmStateViewModel)),
                "update" to confirmStateViewModel.update,
            )
        )

        view.goToConfirmState(confirmStateViewModel, receiver, bitcoinUnitSel.get())
    }

    private fun handleConfirmLightningState(state: ConfirmLightningState) {
        handleConfirmState(ConfirmStateViewModel.fromConfirmLightningState(state))
    }

    private fun handleEditFeeState() {
        view.goToEditFeeState()
    }

    private fun handleErrorState(state: ErrorState) {
        when (state.error) {

            // This error should only reach us if the user scanned a QR with an invalid amount (eg
            // DUST), and is not allowed to change it. There's nothing we can do.
            LibwalletNewOpError.AMOUNT_TOO_SMALL.toString() ->
                handleNewOpError(NewOperationErrorType.AMOUNT_TOO_SMALL)

            LibwalletNewOpError.INVALID_ADDRESS.toString() ->
                handleNewOpError(NewOperationErrorType.INVALID_ADDRESS)

            LibwalletNewOpError.INVOICE_EXPIRED.toString() ->
                handleNewOpError(NewOperationErrorType.INVOICE_EXPIRED)

            else -> {
                Timber.e("Unknown ErrorState:${state.error}")
                handleNewOpError(NewOperationErrorType.GENERIC)
            }
        }
    }

    private fun handleBalanceErrorState(state: BalanceErrorState) {
        when (state.error) {

            LibwalletNewOpError.AMOUNT_GREATER_THAN_BALANCE.toString() ->
                handleNewOpError(NewOperationErrorType.INSUFFICIENT_FUNDS)

            LibwalletNewOpError.UNPAYABLE.toString() ->
                handleNewOpError(NewOperationErrorType.INSUFFICIENT_FUNDS)

            else -> {
                Timber.e("Unknown ErrorState:${state.error}")
                handleNewOpError(NewOperationErrorType.GENERIC)
            }
        }
    }

    private fun handleAbortState() {
        view.showAbortDialog()
    }

    private fun submitOperation(
        confirmStateViewModel: ConfirmStateViewModel,
        swap: SubmarineSwap? = null,
    ) {
        val preparedPayment = PreparedPayment(
            BitcoinAmount.fromLibwallet(confirmStateViewModel.amountInfo.amount),
            BitcoinAmount.fromLibwallet(confirmStateViewModel.onchainFee),
            confirmStateViewModel.note,
            confirmStateViewModel.paymentContext.exchangeRateWindow.windowId,
            confirmStateViewModel.paymentContext.outpoints(),
            confirmStateViewModel.paymentIntent.getPaymentType(),
            contact,
            confirmStateViewModel.paymentIntent.uri.address,
            swap
        )

        try {

            analytics.report(
                E_NEW_OP_SUBMITTED(*uncheckedConvert(opSubmittedMetadata(confirmStateViewModel)))
            )

            submitPayment.run(preparedPayment)
        } catch (error: Throwable) {
            handleError(error)
        }
    }

    private fun reportFinished(operationId: Long, confirmStateViewModel: ConfirmStateViewModel) {
        analytics.report(
            E_NEW_OP_COMPLETED(*opFinishedMetadata(operationId, confirmStateViewModel))
        )
    }

    /**
     * Builds analytics metadata for successfully completed payment event.
     *
     * Combines the operation ID with all submission metadata to track the final completion
     * of the operation. This metadata is used when the operation has been successfully
     * submitted and confirmed by the backend.
     *
     * @param operationId The unique ID assigned to the completed operation
     * @param confirmStateViewModel The confirm state view model containing transaction details
     * @return Array of key-value pairs containing the completion analytics metadata
     */
    private fun opFinishedMetadata(
        operationId: Long,
        confirmStateViewModel: ConfirmStateViewModel,
    ): Array<Pair<String, Any>> {

        val objects = ArrayList<Pair<String, Any>>()
        objects.add(("operation_id" to operationId.toInt()))

        // Also add previously known metadata
        objects.addAll(opSubmittedMetadata(confirmStateViewModel))

        return uncheckedConvert(objects)
    }

    /**
     * Builds comprehensive analytics metadata for payment submission event and events afterwards
     * (e.g payment success or payment error).
     *
     * Collects detailed transaction information including fee details, amounts, swap information,
     * and fee bump data. This metadata is used for analytics events when the user confirms
     * the operation and when it gets submitted to the network. Includes all data from
     * opStartedMetadata plus transaction-specific details.
     *
     * @param stateVm The confirm state view model containing all transaction details
     * @return ArrayList of key-value pairs containing the comprehensive analytics metadata
     */
    private fun opSubmittedMetadata(stateVm: ConfirmStateViewModel): ArrayList<Pair<String, Any>> {
        val paymentType = stateVm.paymentIntent.getPaymentType()
        val payCtx = stateVm.paymentContext
        val selectedFeeRate = Preconditions.checkNotNull(stateVm.feeRateInSatsPerWeight)

        val objects = ArrayList<Pair<String, Any>>()
        val type: AnalyticsEvent.E_FEE_OPTION_TYPE = getFeeOptionTypeParam(selectedFeeRate, payCtx)
        val feeRateInSatsPerVbyte = Rules.toSatsPerVbyte(selectedFeeRate)
        val amount = BitcoinAmount.fromLibwallet(stateVm.amountInfo.amount)
        val fee = BitcoinAmount.fromLibwallet(stateVm.validated.fee)
        val total = BitcoinAmount.fromLibwallet(stateVm.validated.total)
        val onchainFee = BitcoinAmount.fromLibwallet(stateVm.onchainFee)
        val feeNeedsChange = stateVm.validated.feeNeedsChange
        val isOneConf = stateVm.validated.swapInfo?.isOneConf
        val routingFeeInSat = stateVm.validated.swapInfo?.swapFees?.routingFeeInSat
        val confirmationsNeeded = stateVm.validated.swapInfo?.swapFees?.confirmationsNeeded
        val debtType = stateVm.validated.swapInfo?.swapFees?.debtType
        val debtAmountInSat = stateVm.validated.swapInfo?.swapFees?.debtAmountInSat
        val outputAmountInSat = stateVm.validated.swapInfo?.swapFees?.outputAmountInSat
        val outputPaddingInSat = stateVm.validated.swapInfo?.swapFees?.outputPaddingInSat
        val feeBumpSetUUID = stateVm.validated.feeBumpInfo?.setUUID
        val feeBumpAmountInSat = stateVm.validated.feeBumpInfo?.amountInSat
        val feeBumpPolicy = stateVm.validated.feeBumpInfo?.refreshPolicy
        val feeBumpSecondsSinceLastUpdate = stateVm.validated.feeBumpInfo?.secondsSinceLastUpdate

        objects.add(("fee_type" to type.name.lowercase(Locale.getDefault())))
        objects.add(("sats_per_virtual_byte" to feeRateInSatsPerVbyte))
        objects.add(("amount" to SerializationUtils.serializeBitcoinAmount(amount)))
        objects.add(("fee" to SerializationUtils.serializeBitcoinAmount(fee)))
        objects.add(("total" to SerializationUtils.serializeBitcoinAmount(total)))
        objects.add(("onchainFee" to SerializationUtils.serializeBitcoinAmount(onchainFee)))
        objects.add(("feeNeedsChange" to feeNeedsChange))
        objects.add(("isOneConf" to isOneConf.toString()))
        objects.add(("routingFeeInSat" to routingFeeInSat.toString()))
        objects.add(("confirmationsNeeded" to confirmationsNeeded.toString()))
        objects.add(("debtType" to debtType.toString()))
        objects.add(("debtAmountInSat" to debtAmountInSat.toString()))
        objects.add(("outputAmountInSat" to outputAmountInSat.toString()))
        objects.add(("outputPaddingInSat" to outputPaddingInSat.toString()))
        objects.add("fee_bump_set_uuid" to feeBumpSetUUID.toString())
        objects.add("fee_bump_amount_in_sat" to feeBumpAmountInSat.toString())
        objects.add("fee_bump_policy" to feeBumpPolicy.toString())
        objects.add(
            "fee_bump_seconds_since_last_update" to feeBumpSecondsSinceLastUpdate.toString()
        )

        // Also add previously known metadata
        objects.addAll(opStartedMetadata(paymentType))
        return objects
    }

    private fun getFeeOptionTypeParam(
        selectedFeeRate: Double,
        payCtx: newop.PaymentContext,
    ): AnalyticsEvent.E_FEE_OPTION_TYPE {

        // TODO: expose minFeeRateForTarget for newop.PaymentContext (or live with this monstrosity)
        val editFeeState = EditFeeState()
        editFeeState.resolved = Resolved().apply { this.paymentContext = payCtx }

        val fastFeeRate = editFeeState.minFeeRateForTarget(payCtx.feeWindow.fastConfTarget)
        val mediumFeeRate = editFeeState.minFeeRateForTarget(payCtx.feeWindow.mediumConfTarget)
        val slowFeeRate = editFeeState.minFeeRateForTarget(payCtx.feeWindow.slowConfTarget)

        val type: AnalyticsEvent.E_FEE_OPTION_TYPE = when {

            Rules.feeRateEquals(selectedFeeRate, fastFeeRate) ->
                AnalyticsEvent.E_FEE_OPTION_TYPE.FAST

            Rules.feeRateEquals(selectedFeeRate, mediumFeeRate) ->
                AnalyticsEvent.E_FEE_OPTION_TYPE.MEDIUM

            Rules.feeRateEquals(selectedFeeRate, slowFeeRate) ->
                AnalyticsEvent.E_FEE_OPTION_TYPE.SLOW

            else ->
                AnalyticsEvent.E_FEE_OPTION_TYPE.CUSTOM
        }
        return type
    }

    @Suppress("UNCHECKED_CAST")
    private fun uncheckedConvert(pairs: ArrayList<Pair<String, Any>>): Array<Pair<String, Any>> {
        val a = java.lang.reflect.Array.newInstance(Pair::class.java, 0) as Array<Any>
        return pairs.toArray(a) as Array<Pair<String, Any>>
    }

    /**
     * Builds analytics metadata for payment flow started event and events afterwards (e.g
     * intermediate steps like amount selectiong screen, fee selection, payment submission, etc...).
     *
     * Collects basic operation information including payment type, origin, and 2FA status.
     * For Lightning operations (submarine swaps), includes additional debt type and swap UUID.
     * This metadata is used across multiple analytics events during the early stages of
     * the new operation flow (loading, amount entry, description entry).
     *
     * @param paymentType The type of payment being processed (TO_ADDRESS, TO_LN_INVOICE, etc.)
     * @return ArrayList of key-value pairs containing the analytics metadata
     */
    private fun opStartedMetadata(paymentType: Type): ArrayList<Pair<String, Any>> {
        val objects = ArrayList<Pair<String, Any>>()
        objects.add(("type" to getEventType(paymentType)))
        objects.add(("origin" to getEventOrigin(origin)))

        if (submarineSwap != null && submarineSwap!!.fundingOutput.debtType != null) {
            objects.add(("debt_type" to submarineSwap!!.fundingOutput.debtType!!))
            objects.add(("swap_uuid" to submarineSwap!!.houstonUuid))
        }

        objects.add("has_2fa" to has2fa.toString())

        return objects
    }
}