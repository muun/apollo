package io.muun.apollo.presentation.ui.operation_detail;

import io.muun.apollo.R;
import io.muun.apollo.data.preferences.BlockchainHeightRepository;
import io.muun.apollo.domain.action.OperationActions;
import io.muun.apollo.domain.model.CurrencyDisplayMode;
import io.muun.apollo.domain.model.Operation;
import io.muun.apollo.domain.selector.CurrencyDisplayModeSelector;
import io.muun.apollo.presentation.analytics.AnalyticsEvent;
import io.muun.apollo.presentation.analytics.AnalyticsEvent.S_MORE_INFO_TYPE;
import io.muun.apollo.presentation.model.UiOperation;
import io.muun.apollo.presentation.ui.base.BasePresenter;
import io.muun.apollo.presentation.ui.base.di.PerActivity;
import io.muun.apollo.presentation.ui.utils.LinkBuilder;
import io.muun.common.Optional;

import android.os.Bundle;
import icepick.State;
import rx.Observable;

import javax.inject.Inject;
import javax.validation.constraints.NotNull;

@PerActivity
public class OperationDetailPresenter extends BasePresenter<OperationDetailView> {

    public static final String OPERATION_ID_KEY = "OPERATION_ID";

    private final OperationActions operationActions;
    private final CurrencyDisplayModeSelector currencyDisplayModeSel;

    private final LinkBuilder linkBuilder;

    @State
    protected long operationId;

    @State
    protected CurrencyDisplayMode currencyDisplayMode;

    private final BlockchainHeightRepository blockchainHeightRepository;

    /**
     * Constructor.
     */
    @Inject
    public OperationDetailPresenter(OperationActions operationActions,
                                    CurrencyDisplayModeSelector currencyDisplayModeSel,
                                    LinkBuilder linkBuilder,
                                    BlockchainHeightRepository blockchainHeightRepository) {

        this.operationActions = operationActions;
        this.currencyDisplayModeSel = currencyDisplayModeSel;
        this.linkBuilder = linkBuilder;
        this.blockchainHeightRepository = blockchainHeightRepository;
    }

    @Override
    public void setUp(@NotNull Bundle arguments) {
        super.setUp(arguments);

        final Optional<Long> maybeOperationId = takeLongArgument(arguments, OPERATION_ID_KEY);
        checkArgument(maybeOperationId.isPresent(), "operationId");

        currencyDisplayMode = currencyDisplayModeSel.get();

        operationId = maybeOperationId.get();
        bindOperation();
    }

    private void bindOperation() {
        final Observable<UiOperation> observable = operationActions
                .fetchOperationById(operationId)
                .doOnNext(this::reportOperationDetail)
                .map(op -> UiOperation.fromOperation(
                        op,
                        linkBuilder,
                        currencyDisplayMode,
                        getContext())
                )
                .compose(getAsyncExecutor())
                .doOnNext(view::setOperation);

        subscribeTo(observable);
    }

    /**
     * Copy LN invoice to the clipboard.
     */
    public void copyLnInvoiceToClipboard(String invoice) {
        clipboardManager.copy("Lightning invoice", invoice);
    }

    /**
     * Copy swap preimage to the clipboard.
     */
    public void copySwapPreimageToClipboard(String preimage) {
        clipboardManager.copy("Swap preimage", preimage);
    }

    /**
     * Copy transaction id/hash to the clipboard.
     */
    public void copyTransactionIdToClipboard(String transactionId) {
        clipboardManager.copy("Transaction ID", transactionId);
    }

    /**
     * Copy fee amount to the clipboard.
     */
    public void copyNetworkFeeToClipboard(String fee) {
        clipboardManager.copy("Network fee", fee);
    }

    /**
     * Copy amount to the clipboard.
     */
    public void copyAmountToClipboard(String amount) {
        clipboardManager.copy("Amount", amount);
    }

    /**
     * Fire the SHARE intent with a given transaction ID.
     */
    public void shareTransactionId(String transactionId) {
        final String title = getContext().getString(R.string.operation_detail_share_txid_title);
        final String text = linkBuilder.rawTransactionLink(transactionId);

        navigator.shareText(getContext(), text, title);
    }

    public int getBlockchainHeight() {
        return blockchainHeightRepository.fetchLatest();
    }

    private void reportOperationDetail(Operation op) {
        analytics.report(new AnalyticsEvent.S_OPERATION_DETAIL((int) operationId, op.direction));
    }

    /**
     * Report analytics event of screen view event of Lightning "Confirmation Needed, why this?"
     * dialog.
     */
    public void reportShowConfirmationNeededInfo() {
        analytics.report(new AnalyticsEvent.S_MORE_INFO(S_MORE_INFO_TYPE.CONFIRMATION_NEEDED));
    }
}
