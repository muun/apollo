package io.muun.apollo.presentation.ui.new_operation;

import io.muun.apollo.domain.action.operation.ResolveOperationUriAction;
import io.muun.apollo.domain.action.operation.SubmitPaymentAction;
import io.muun.apollo.domain.action.realtime.FetchRealTimeDataAction;
import io.muun.apollo.domain.errors.AmountTooSmallError;
import io.muun.apollo.domain.errors.CyclicalSwapError;
import io.muun.apollo.domain.errors.ExchangeRateWindowTooOldError;
import io.muun.apollo.domain.errors.InsufficientFundsError;
import io.muun.apollo.domain.errors.InvalidInvoiceException;
import io.muun.apollo.domain.errors.InvalidSwapException;
import io.muun.apollo.domain.errors.InvoiceAlreadyUsedException;
import io.muun.apollo.domain.errors.InvoiceExpiredException;
import io.muun.apollo.domain.errors.InvoiceExpiresTooSoonException;
import io.muun.apollo.domain.errors.InvoiceMissingAmountException;
import io.muun.apollo.domain.errors.NoPaymentRouteException;
import io.muun.apollo.domain.errors.UnreachableNodeException;
import io.muun.apollo.domain.errors.UserFacingError;
import io.muun.apollo.domain.model.CurrencyDisplayMode;
import io.muun.apollo.domain.model.FeeOption;
import io.muun.apollo.domain.model.Operation;
import io.muun.apollo.domain.model.OperationUri;
import io.muun.apollo.domain.model.PaymentAnalysis;
import io.muun.apollo.domain.model.PaymentContext;
import io.muun.apollo.domain.model.PaymentRequest;
import io.muun.apollo.domain.model.PreparedPayment;
import io.muun.apollo.domain.model.WithPaymentContext;
import io.muun.apollo.domain.selector.CurrencyDisplayModeSelector;
import io.muun.apollo.domain.selector.ExchangeRateSelector;
import io.muun.apollo.domain.selector.PaymentContextSelector;
import io.muun.apollo.domain.selector.UserSelector;
import io.muun.apollo.presentation.analytics.AnalyticsEvent;
import io.muun.apollo.presentation.analytics.AnalyticsEvent.S_MORE_INFO_TYPE;
import io.muun.apollo.presentation.ui.base.BasePresenter;
import io.muun.apollo.presentation.ui.base.di.PerActivity;
import io.muun.apollo.presentation.ui.fragments.new_op_error.NewOperationErrorParentPresenter;
import io.muun.apollo.presentation.ui.helper.MoneyHelper;
import io.muun.common.Optional;
import io.muun.common.Rules;
import io.muun.common.utils.Preconditions;

import android.os.Bundle;
import androidx.annotation.VisibleForTesting;
import kotlin.Pair;
import org.javamoney.moneta.Money;
import rx.Observable;
import timber.log.Timber;

import java.lang.reflect.Array;
import java.util.ArrayList;
import javax.inject.Inject;
import javax.money.CurrencyUnit;
import javax.money.MonetaryAmount;
import javax.validation.constraints.NotNull;

import static io.muun.common.utils.Preconditions.checkNotNull;


@PerActivity
public class NewOperationPresenter extends BasePresenter<NewOperationView> implements
        WithPaymentContext,
        NewOperationErrorParentPresenter {

    private final SubmitPaymentAction submitPaymentAction;

    private final FetchRealTimeDataAction fetchRealTimeData;

    @VisibleForTesting
    final ResolveOperationUriAction resolveOperationUri;

    @VisibleForTesting
    final PaymentContextSelector paymentContextSel;

    final CurrencyDisplayModeSelector currencyDisplayModeSel;
    final UserSelector userSel;
    final ExchangeRateSelector exchangeRateSelector;

    @VisibleForTesting
    NewOperationForm form;

    private NewOperationOrigin origin;

    /**
     * Creates a NewOperationPresenter.
     */
    @Inject
    public NewOperationPresenter(ResolveOperationUriAction resolveOperationUri,
                                 PaymentContextSelector paymentContextSel,
                                 SubmitPaymentAction submitPaymentAction,
                                 FetchRealTimeDataAction fetchRealTimeData,
                                 CurrencyDisplayModeSelector currencyDisplayModeSel,
                                 UserSelector userSel,
                                 ExchangeRateSelector exchangeRateSelector) {

        this.resolveOperationUri = resolveOperationUri;
        this.submitPaymentAction = submitPaymentAction;
        this.paymentContextSel = paymentContextSel;
        this.fetchRealTimeData = fetchRealTimeData;
        this.currencyDisplayModeSel = currencyDisplayModeSel;
        this.userSel = userSel;
        this.exchangeRateSelector = exchangeRateSelector;
    }

    /**
     * Call in onCreate, to perform a one-time setup.
     */
    public void onViewCreated(OperationUri operationUri, NewOperationOrigin origin) {
        setUpCurrencyDisplayMode(); // this shouldn't happen here, but this Activity is a mess :(

        // NOTE: form may be non-null if Activity was destroyed (since we outlive activities).
        // We also call `setForm` in `setUp` (onResume), but that's not enough: `setForm` needs to
        // run before external results from Activities arrive (eg fee selection).
        if (this.form != null) {
            view.setForm(this.form);    // onViewRecreated

        } else {
            this.form = new NewOperationForm(operationUri);
        }

        if (this.origin == null) {
            this.origin = origin;
        }

        fetchRealTimeData.run(); // if it's already running (eg ran by home screen), no problem.
    }

    @Override
    public void setUp(@NotNull Bundle arguments) {
        super.setUp(arguments);

        setUpCurrencyDisplayMode();
        setUpSubmitOperationAction();
        setUpFetchRealTimeData();

        view.setForm(form);
    }

    private void setUpCurrencyDisplayMode() {
        view.setCurrencyDisplayMode(currencyDisplayModeSel.get());
    }

    void setUpFetchRealTimeData() {
        final Observable<?> observable = fetchRealTimeData
                .getState()
                .compose(handleStates(view::setLoading, this::handleError))
                .doOnNext(i -> onRealTimeDataReady());

        subscribeTo(observable);
    }

    @VisibleForTesting
    void setUpPaymentContext() {
        final Observable<?> observable = paymentContextSel
                .watch()
                .compose(getAsyncExecutor())
                .first()
                .doOnNext(this::onPaymentContextChanged);

        subscribeTo(observable);
    }

    @VisibleForTesting
    void setUpResolveUriAction() {
        final Observable<?> observableUri = resolveOperationUri
                .getState()
                .compose(handleStates(view::setLoading, this::handleError))
                .doOnNext(this::onPaymentRequestReady);

        subscribeTo(observableUri);
    }

    @VisibleForTesting
    void setUpSubmitOperationAction() {
        final Observable<Operation> observable = submitPaymentAction
                .getState()
                .compose(handleStates(null, this::handleError))
                .doOnNext(this::onSubmitSuccessful);

        subscribeTo(observable);
    }

    /**
     * Set the amount.
     */
    public void updateAmount(MonetaryAmount amount) {
        form.amount = amount;

        final CurrencyDisplayMode currencyDisplayMode = currencyDisplayModeSel.get();

        final PaymentContext payCtx = getPaymentContext();
        final MonetaryAmount balance = payCtx
                .convert(payCtx.getUserBalance(), amount.getCurrency());

        final String renderedAmount = MoneyHelper.formatLongMonetaryAmount(
                amount,
                false,
                currencyDisplayMode
        );

        final String renderedBalance = MoneyHelper.formatLongMonetaryAmount(
                balance,
                false,
                currencyDisplayMode
        );

        // NOTE: when the user manually enters the same number he sees as "available balance", we
        // automatically trigger use-all-funds. Since the actual balance contains decimals the user
        // is not seeing (because of rounding at presentation-level code), we have to detect this
        // case using this odd-looking code:
        if (renderedAmount.equals(renderedBalance)) {
            setUsingAllFunds();
        } else {
            form.isUsingAllFunds = false;
        }

        onPaymentDetailsChanged();
    }

    /**
     * Un/Confirm the amount, typically meaning going forward after/back to enter amount step.
     */
    public void setAmountConfirmed(boolean isConfirmed) {
        form.isAmountConfirmed = isConfirmed;
        form.displayInAlternateCurrency = false;

        // When unconfirming an amount, reset useAllFunds flag. Otherwise we can't get out of TFFA
        if (!isConfirmed) {
            form.isUsingAllFunds = false;
        }

        onPaymentDetailsChanged();
    }

    public boolean canUseAllFunds() {
        return getPaymentContext().getUserBalance() > 0;
    }

    /**
     * Set the max amount, auto-confirm and enable the `isUsingAllFunds` flag.
     */
    public void confirmAmountUseAllFunds() {
        setUsingAllFunds();
        form.isAmountConfirmed = true;
        view.setForm(form);

        onPaymentDetailsChanged();
    }

    private void setUsingAllFunds() {
        final CurrencyUnit inputCurrency = form.amount.getCurrency();
        final long totalBalanceInSatoshis = getPaymentContext().getUserBalance();

        form.amount = getPaymentContext().convert(totalBalanceInSatoshis, inputCurrency);
        form.isUsingAllFunds = true;
    }

    /**
     * Set the description.
     */
    public void updateDescription(String description) {
        if (description.equals(form.description)) {
            return; // this is here because description cannot be set without triggering listeners
        }

        form.description = description;
        onPaymentDetailsChanged();
    }

    public void setDescriptionConfirmed(boolean isConfirmed) {
        form.isDescriptionConfirmed = isConfirmed;
        onPaymentDetailsChanged();
    }

    public void updateFeeRate(double satoshisPerByte) {
        form.selectedFeeRate = satoshisPerByte;
        onPaymentDetailsChanged();
    }

    public void setDisplayInAlternateCurrency(boolean displayInAlternateCurrency) {
        form.displayInAlternateCurrency = displayInAlternateCurrency;
        onPaymentDetailsChanged(); // this is overkill to re-display, but current flow requires it
    }

    /**
     * Finishes the process by sending the operation to Houston.
     */
    public void confirmOperation() {
        view.setLoading(true);
        final PaymentRequest updatedPayReq = getUpdatedPaymentRequest(form);

        try {
            final PreparedPayment prepPayment = getPaymentContext().prepare(updatedPayReq);
            form.submitedPayReq = prepPayment.payReq;
            trackSubmit(prepPayment);
            submitPaymentAction.run(prepPayment);

        } catch (Throwable error) {
            handleError(error);
        }
    }

    private void trackSubmit(final PreparedPayment prepPayment) {

        analytics.report(new AnalyticsEvent.E_NEW_OP_SUBMITTED(
                uncheckedConvert(buildNewOpSubmittedMetadata(prepPayment.payReq))
        ));
    }

    /**
     * Starts custom fee selection flow.
     */
    public void editFee() {
        view.editFee(getUpdatedPaymentRequest(form));
    }

    @Override
    protected void onNetworkConnectionChange(boolean isConnected) {
        view.setConnectedToNetwork(isConnected);
    }

    private void onRealTimeDataReady() {
        // Now that we have updated exchange and fee rates, we can proceed with our preparations.
        // This is especially important if we landed here after the user clicked an external link,
        // since he skipped the home screen and didn't automatically fetch RTD.

        // Note that RTD fetching is instantaneous if itwas already up to date.

        PaymentContext.Companion.setCurrentlyInUse(null); // invalidate payCtx to trigger re-fetch
        setUpPaymentContext();

        // Obtain the payment request (if not already done, this may not be the first RTD fetch):
        if (!getPaymentRequest().isPresent()) {
            resolveOperationUri.reset();
            resolveOperationUri.run(this.form.operationUri);
        }
    }

    private void onPaymentRequestReady(PaymentRequest payReq) {
        Preconditions.checkNull(form.payReq);

        form.payReq = payReq;
        reportStarted(payReq);

        if (payReq.getAmount() != null) {
            form.amount = payReq.getAmount();
            form.isAmountConfirmed = payReq.getAmount().isPositive(); // may change after validation
            form.isAmountFixed = payReq.getAmount().isPositive(); // sometimes "fixed" as zero

        } else {
            form.amount = Money
                    .of(0, userSel.get().getPrimaryCurrency(exchangeRateSelector.getWindow()));
        }

        if (payReq.getDescription() != null) {
            form.description = payReq.getDescription();
            form.isDescriptionConfirmed = !payReq.getDescription().isEmpty();
        }

        if (payReq.getSwap() != null) {
            form.isFeeFixed = true;
        }

        view.setForm(form);

        onPaymentDetailsChanged();
    }

    private void onPaymentContextChanged(PaymentContext newPaymentContext) {
        // Avoid changing paymentContext onResume but handle analysis
        if (!hasPaymentContext()) {
            PaymentContext.Companion.setCurrentlyInUse(newPaymentContext);

            // Without PaymentContext, we can't properly handle errors in the resolve action. So
            // we subscribe now that it's available:
            setUpResolveUriAction();
        }

        onPaymentDetailsChanged();
    }

    private void onSubmitSuccessful(Operation operation) {
        reportFinished(getPaymentRequest().get(), operation.getHid());

        final PaymentRequest payReq = form.payReq;
        checkNotNull(payReq);

        navigator.navigateToHome(getContext());
        view.finishActivity();
    }

    @VisibleForTesting
    void onPaymentDetailsChanged() {
        if (!hasPaymentContext() || form == null || form.payReq == null) {
            return;
        }

        try {
            final PaymentRequest updatedPayReq = getUpdatedPaymentRequest(form);
            final PaymentAnalysis analysis = getPaymentContext().analyze(updatedPayReq);

            onPaymentAnalysisChanged(analysis);

        } catch (Throwable error) {
            handleError(error);
        }
    }

    private void onPaymentAnalysisChanged(PaymentAnalysis analysis) {
        // Let's check for some errors that may cause us to leave this screen:
        final boolean canPayAmountOrChangeIt = analysis.getCanPayWithMinimumFee()
                || !form.isAmountFixed;

        final boolean canPayFeeOrChangeIt = analysis.getCanPayWithSelectedFee()
                || (analysis.getCanPayWithMinimumFee() && !form.isFeeFixed);

        final boolean isConfirmedButCannotPay = form.isAmountConfirmed
                && !analysis.getCanPayWithMinimumFee();

        // These two cases (can't pay amount / can pay amount but not fee) are merged in UI:
        if (!canPayAmountOrChangeIt && !canPayFeeOrChangeIt) {
            handleError(new InsufficientFundsError());
            return;
        }

        if (isConfirmedButCannotPay) {
            handleError(new InsufficientFundsError());
            return;
        }

        if (analysis.isAmountTooSmall() && form.isAmountFixed) {
            handleError(new AmountTooSmallError(analysis.getOutputAmount().inSatoshis));
            return;
        }

        view.setPaymentAnalysis(analysis);
    }

    private PaymentRequest getUpdatedPaymentRequest(NewOperationForm form) {
        // We don't want any update or re-calculation after payment submission
        if (form.submitedPayReq != null) {
            return form.submitedPayReq;
        }

        final PaymentRequest paymentRequest = checkNotNull(form.payReq)
                .withChanges(form.amount, form.description)
                .withTakeFeeFromAmount(form.isUsingAllFunds);

        final Double feeRate = getFeeRate();
        if (feeRate != null) {
            return paymentRequest.withFeeRate(feeRate);
        }

        return paymentRequest;
    }

    private Double getFeeRate() {
        if (form.selectedFeeRate != null) {
            return form.selectedFeeRate;

        } else if (form.payReq != null) {
            return form.payReq.getFeeInSatoshisPerByte(); // can be null for AmountLess Invoices

        } else {
            return getPaymentContext().getFastFeeOption().getSatoshisPerByte();
        }
    }

    @Override
    public void handleError(Throwable error) {
        view.setLoading(false);
        super.handleError(error);
    }

    @Override
    protected boolean maybeHandleNonFatalError(Throwable error) {
        if (error instanceof UnreachableNodeException) {
            showErrorScreen(NewOperationErrorType.INVOICE_UNREACHABLE_NODE);

        } else if (error instanceof NoPaymentRouteException) {
            showErrorScreen(NewOperationErrorType.INVOICE_NO_ROUTE);

        } else if (error instanceof InvoiceExpiresTooSoonException) {
            showErrorScreen(NewOperationErrorType.INVOICE_WILL_EXPIRE_SOON);

        } else if (error instanceof InvoiceExpiredException) {
            showErrorScreen(NewOperationErrorType.INVOICE_EXPIRED);

        } else if (error instanceof InvoiceAlreadyUsedException) {
            showErrorScreen(NewOperationErrorType.INVOICE_ALREADY_USED);

        } else if (error instanceof InvoiceMissingAmountException) {
            showErrorScreen(NewOperationErrorType.INVOICE_MISSING_AMOUNT);

        } else if (error instanceof InvalidInvoiceException) {
            showErrorScreen(NewOperationErrorType.INVALID_INVOICE);

        } else if (error instanceof InvalidSwapException) {
            showErrorScreen(NewOperationErrorType.INVALID_SWAP);

        } else if (error instanceof InsufficientFundsError) {
            showErrorScreen(NewOperationErrorType.INSUFFICIENT_FUNDS);

        } else if (error instanceof ExchangeRateWindowTooOldError) {
            showErrorScreen(NewOperationErrorType.EXCHANGE_RATE_WINDOW_TOO_OLD);

        } else if (error instanceof CyclicalSwapError) {
            showErrorScreen(NewOperationErrorType.CYCLICAL_SWAP);

        } else if (error instanceof AmountTooSmallError) {
            // This error should only reach us if the user scanned a QR with an invalid amount (eg
            // DUST), and is not allowed to change it. There's nothing we can do.
            showErrorScreen(NewOperationErrorType.AMOUNT_TOO_SMALL);

        } else if (error instanceof UserFacingError) {
            showErrorScreen(NewOperationErrorType.GENERIC);

        } else if (!isOperationUriResolved()) {
            // If the error happened during resolve, run our custom handling before delegating:
            return maybeHandleErrorDuringResolve(error) || super.maybeHandleNonFatalError(error);

        } else {
            return super.maybeHandleNonFatalError(error);
        }

        return true;
    }

    @Override
    protected boolean maybeHandleUnknownError(Throwable error) {
        // If the error happened during resolve, run our custom handling before delegating:
        return maybeHandleErrorDuringResolve(error) || super.maybeHandleUnknownError(error);
    }

    private boolean maybeHandleErrorDuringResolve(Throwable error) {
        if (isOperationUriResolved()) {
            return false; // not our job
        }

        // We don't have a predefined error screen for this exception, but we know it happened
        // during the resolve step. We have nothing meaningful to show the user, and every case
        // that falls here is most likely a problem on our side.
        Timber.e(error);
        analytics.report(new AnalyticsEvent.E_NEW_OP_ERROR());

        showErrorScreen(NewOperationErrorType.GENERIC);
        return true;
    }

    private boolean isOperationUriResolved() {
        return (form.payReq != null);
    }

    void showErrorScreen(NewOperationErrorType errorType) {
        view.showErrorScreen(errorType);
    }

    public void goHomeInDefeat() {
        view.finishActivity();
    }

    /**
     * Get the resolved PaymentRequest, if available (won't be on resolved errors).
     */
    public Optional<PaymentRequest> getPaymentRequest() {
        if (form.payReq != null) {
            return Optional.of(getUpdatedPaymentRequest(form));
        } else {
            return Optional.empty();
        }
    }

    public void reportError(NewOperationErrorType type) {
        analytics.report(getErrorEvent(type));
    }

    private AnalyticsEvent.S_NEW_OP_ERROR getErrorEvent(NewOperationErrorType type) {
        return new AnalyticsEvent.S_NEW_OP_ERROR(
                getEventOrigin(origin),
                getErrorEventType(type)
        );
    }

    private AnalyticsEvent.S_NEW_OP_ERROR_TYPE getErrorEventType(NewOperationErrorType type) {
        switch (type) {
            case AMOUNT_TOO_SMALL:
                return AnalyticsEvent.S_NEW_OP_ERROR_TYPE.AMOUNT_BELOW_DUST;

            case INSUFFICIENT_FUNDS:
                return AnalyticsEvent.S_NEW_OP_ERROR_TYPE.INSUFFICIENT_FUNDS;

            case INVOICE_NO_ROUTE:
                return AnalyticsEvent.S_NEW_OP_ERROR_TYPE.NO_PAYMENT_ROUTE;

            case INVOICE_EXPIRED:
                return AnalyticsEvent.S_NEW_OP_ERROR_TYPE.EXPIRED_INVOICE;

            case INVOICE_WILL_EXPIRE_SOON:
                return AnalyticsEvent.S_NEW_OP_ERROR_TYPE.INVOICE_EXPIRES_TOO_SOON;

            case INVOICE_ALREADY_USED:
                return AnalyticsEvent.S_NEW_OP_ERROR_TYPE.INVOICE_ALREADY_USED;

            case INVALID_INVOICE:
                return AnalyticsEvent.S_NEW_OP_ERROR_TYPE.INVALID_INVOICE;

            case INVOICE_MISSING_AMOUNT:
                return AnalyticsEvent.S_NEW_OP_ERROR_TYPE.INVOICE_MISSING_AMOUNT;

            case EXCHANGE_RATE_WINDOW_TOO_OLD:
                return AnalyticsEvent.S_NEW_OP_ERROR_TYPE.EXCHANGE_RATE_WINDOW_TOO_OLD;

            case INVALID_SWAP:
                return AnalyticsEvent.S_NEW_OP_ERROR_TYPE.INVALID_SWAP;

            default:
                return AnalyticsEvent.S_NEW_OP_ERROR_TYPE.OTHER;
        }
    }

    private void reportStarted(PaymentRequest paymentRequest) {
        analytics.report(new AnalyticsEvent.E_NEW_OP_STARTED(
                uncheckedConvert(buildNewOpStartedMetadata(paymentRequest))
        ));
    }

    private void reportFinished(PaymentRequest paymentRequest, long operationId) {
        analytics.report(new AnalyticsEvent.E_NEW_OP_COMPLETED(
                checkNotNull(buildNewOpFinishedMetadata(operationId, paymentRequest))
        ));
    }

    private Pair<String, ?>[] buildNewOpFinishedMetadata(long operationId, PaymentRequest payReq) {

        final ArrayList<Pair<String, Object>> objects = new ArrayList<>();

        objects.add(new Pair<>("operation_id", (int) operationId));

        // Also add previously known metadata
        objects.addAll(buildNewOpSubmittedMetadata(payReq));

        return uncheckedConvert(objects);
    }

    private ArrayList<Pair<String, Object>> buildNewOpSubmittedMetadata(PaymentRequest payReq) {

        final ArrayList<Pair<String, Object>> objects = new ArrayList<>();

        final double selectedFeeRate = Preconditions.checkNotNull(payReq.getFeeInSatoshisPerByte());

        final AnalyticsEvent.E_FEE_OPTION_TYPE type = getFeeOptionTypeParam(selectedFeeRate);
        final double feeRateInSatsPerVbyte = Rules.toSatsPerVbyte(selectedFeeRate);

        objects.add(new Pair<>("fee_type", type.name().toLowerCase()));
        objects.add(new Pair<>("sats_per_virtual_byte", feeRateInSatsPerVbyte));

        // Also add previously known metadata
        objects.addAll(buildNewOpStartedMetadata(payReq));


        return objects;
    }

    private AnalyticsEvent.E_FEE_OPTION_TYPE getFeeOptionTypeParam(double selectedFeeRate) {

        final PaymentContext payCtx = getPaymentContext();
        final FeeOption fast = payCtx.getFastFeeOption();
        final FeeOption mid = payCtx.getMediumFeeOption();
        final FeeOption slow = payCtx.getSlowFeeOption();

        final AnalyticsEvent.E_FEE_OPTION_TYPE type;

        if (Rules.feeRateEquals(selectedFeeRate, fast.getSatoshisPerByte())) {
            type = AnalyticsEvent.E_FEE_OPTION_TYPE.FAST;

        } else if (Rules.feeRateEquals(selectedFeeRate, mid.getSatoshisPerByte())) {
            type = AnalyticsEvent.E_FEE_OPTION_TYPE.MEDIUM;

        } else if (Rules.feeRateEquals(selectedFeeRate, slow.getSatoshisPerByte())) {
            type = AnalyticsEvent.E_FEE_OPTION_TYPE.SLOW;

        } else {
            type = AnalyticsEvent.E_FEE_OPTION_TYPE.CUSTOM;
        }

        return type;
    }

    private static AnalyticsEvent.E_NEW_OP_TYPE getEventType(PaymentRequest payReq) {
        return AnalyticsEvent.E_NEW_OP_TYPE.fromModel(payReq.getType());
    }

    private static AnalyticsEvent.S_NEW_OP_ORIGIN getEventOrigin(NewOperationOrigin origin) {
        return AnalyticsEvent.S_NEW_OP_ORIGIN.fromModel(origin);
    }

    private ArrayList<Pair<String, Object>> buildNewOpStartedMetadata(PaymentRequest payReq) {
        final ArrayList<Pair<String, Object>> objects = new ArrayList<>();

        objects.add(new Pair<>("type", getEventType(payReq)));
        objects.add(new Pair<>("origin", getEventOrigin(origin)));

        if (payReq.getSwap() != null && payReq.getSwap().getFundingOutput().getDebtType() != null) {
            objects.add(new Pair<>("debt_type", payReq.getSwap().getFundingOutput().getDebtType()));
        }

        return objects;
    }

    @SuppressWarnings("unchecked")
    private Pair<String, ?>[] uncheckedConvert(ArrayList<Pair<String, Object>> pairs) {
        final Pair<String, ?>[] a = (Pair<String, ?>[]) Array.newInstance(Pair.class, 0);
        return (Pair<String, ?>[]) pairs.toArray(a);
    }

    public void reportShowDestinationInfo() {
        analytics.report(new AnalyticsEvent.S_MORE_INFO(S_MORE_INFO_TYPE.NEW_OP_DESTINATION));
    }

    public void handleLnUrl(String lnurl) {
        navigator.navigateToLnUrlWithdraw(getContext(), lnurl);
    }
}
