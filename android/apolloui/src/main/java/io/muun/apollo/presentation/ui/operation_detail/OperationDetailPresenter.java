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
import org.bitcoinj.core.NetworkParameters;
import rx.Observable;

import javax.inject.Inject;
import javax.validation.constraints.NotNull;

@PerActivity
public class OperationDetailPresenter extends BasePresenter<OperationDetailView> {

    public static final String OPERATION_ID_KEY = "OPERATION_ID";

    private final OperationActions operationActions;
    private final CurrencyDisplayModeSelector currencyDisplayModeSel;

    private final NetworkParameters networkParameters;
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
                                    NetworkParameters networkParameters,
                                    LinkBuilder linkBuilder,
                                    BlockchainHeightRepository blockchainHeightRepository) {

        this.operationActions = operationActions;
        this.currencyDisplayModeSel = currencyDisplayModeSel;
        this.networkParameters = networkParameters;
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
                .map(operation -> UiOperation.fromOperation(
                        operation,
                        networkParameters,
                        linkBuilder,
                        currencyDisplayMode
                ))
                .compose(getAsyncExecutor())
                .doOnNext(view::setOperation);

        subscribeTo(observable);
    }

    public void copyLnInvoiceToClipboard(String invoice) {
        operationActions.copyLnInvoiceToClipboard(invoice);
    }

    public void copySwapPreimageToClipboard(String preimage) {
        operationActions.copySwapPreimageToClipboard(preimage);
    }

    public void copyTransactionIdToClipboard(String transactionId) {
        operationActions.copyTransactionIdToClipboard(transactionId);
    }

    public void copyNetworkFeeToClipboard(String fee) {
        operationActions.copyNetworkFeeToClipboard(fee);
    }

    public void copyAmountToClipboard(String amount) {
        operationActions.copyAmountToClipboard(amount);
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

    public void reportShowConfirmationNeededInfo() {
        analytics.report(new AnalyticsEvent.S_MORE_INFO(S_MORE_INFO_TYPE.CONFIRMATION_NEEDED));
    }
}
