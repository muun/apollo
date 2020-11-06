package io.muun.apollo.domain.action;

import io.muun.apollo.data.db.base.ElementNotFoundException;
import io.muun.apollo.data.db.operation.OperationDao;
import io.muun.apollo.data.net.HoustonClient;
import io.muun.apollo.data.os.ClipboardProvider;
import io.muun.apollo.data.preferences.TransactionSizeRepository;
import io.muun.apollo.data.preferences.UserRepository;
import io.muun.apollo.domain.action.base.AsyncActionStore;
import io.muun.apollo.domain.action.operation.CreateOperationAction;
import io.muun.apollo.domain.action.operation.OperationMetadataMapper;
import io.muun.apollo.domain.model.NextTransactionSize;
import io.muun.apollo.domain.model.Operation;
import io.muun.common.Optional;
import io.muun.common.rx.ObservableFn;
import io.muun.common.rx.RxHelper;

import rx.Observable;
import timber.log.Timber;

import javax.inject.Inject;
import javax.inject.Singleton;


@Singleton
public class OperationActions {

    private final CreateOperationAction createOperation;

    private final OperationDao operationDao;

    private final UserRepository userRepository;
    private final TransactionSizeRepository transactionSizeRepository;

    private final HoustonClient houstonClient;
    private final ClipboardProvider clipboardProvider;

    private final OperationMetadataMapper operationMapper;


    /**
     * Constructor.
     */
    @Inject
    public OperationActions(CreateOperationAction createOperation,
                            OperationDao operationDao,
                            UserRepository userRepository,
                            TransactionSizeRepository transactionSizeRepository,
                            HoustonClient houstonClient,
                            ClipboardProvider clipboardProvider,
                            AsyncActionStore asyncActionStore,
                            OperationMetadataMapper operationMapper) {

        this.createOperation = createOperation;

        this.operationDao = operationDao;

        this.userRepository = userRepository;
        this.transactionSizeRepository = transactionSizeRepository;

        this.houstonClient = houstonClient;
        this.clipboardProvider = clipboardProvider;

        this.operationMapper = operationMapper;
    }

    /**
     * Copy an external address to the system clipboard.
     */
    public void copyAddressToClipboard(String address) {
        clipboardProvider.copy("Bitcoin address", address);
        userRepository.setLastCopiedAddress(address);
    }

    /**
     * Copy a Lightning Invoice to the system clipboard.
     */
    public void copyLnInvoiceToClipboard(String invoice) {
        clipboardProvider.copy("Lightning invoice", invoice);
    }

    /**
     * Copy a Submarine Swap Payment Preimage to the system clipboard.
     */
    public void copySwapPreimageToClipboard(String preimage) {
        clipboardProvider.copy("Swap preimage", preimage);
    }

    public void copyTransactionIdToClipboard(String transactionId) {
        clipboardProvider.copy("Transaction ID", transactionId);
    }

    public void copyNetworkFeeToClipboard(String fee) {
        clipboardProvider.copy("Network fee", fee);
    }

    public void copyAmountToClipboard(String amount) {
        clipboardProvider.copy("Amount", amount);
    }

    /**
     * Fetch the complete operation list from Houston.
     */
    public Observable<Void> fetchReplaceOperations() {
        Timber.d("[Operations] Fetching full operation list");

        return operationDao.deleteAll().flatMap(ignored ->
                houstonClient.fetchOperations()
                        .flatMap(Observable::from)
                        // using concatMap to avoid parallelization, overflows JobExecutor's queue
                        // TODO use batching
                        .map(operationMapper::mapFromMetadata)
                        .onBackpressureBuffer(200)
                        .concatMap(createOperation::saveOperation)
                        .lastOrDefault(null)
                        .map(RxHelper::toVoid)
        );
    }

    /**
     * Watch the total balance of the wallet.
     */
    public Observable<Long> watchBalance() {
        return watchValidNextTransactionSize()
                .filter(validOrNull -> validOrNull != null) // waiting for update
                .map(t -> {
                    if (t.sizeProgression == null || t.sizeProgression.isEmpty()) {
                        return 0L;
                    }

                    return t.sizeProgression.get(t.sizeProgression.size() - 1).amountInSatoshis;
                });
    }

    /**
     * Fetches a single operation from the database, by id.
     */
    public Observable<Operation> fetchOperationById(Long operationId) {

        return operationDao.fetchById(operationId);
    }

    // ---------------------------------------------------------------------------------------------
    // Private helpers

    /**
     * Return the stored NextTransactionSize if available and up-to-date.
     */
    private Observable<NextTransactionSize> watchValidNextTransactionSize() {
        return transactionSizeRepository
                .watchNextTransactionSize()
                .map(transactionSize -> {
                    if (transactionSize == null) {
                        return null; // no local value available
                    }

                    final long validAtOperationHid = Optional
                            .ofNullable(transactionSize.validAtOperationHid)
                            .orElse(0L);

                    final long latestOperationHid = getLatestOperation()
                            .map(Operation::getHid)
                            .orElse(0L);

                    // NOTE: if an Operation has been made, giving us new UTXOs (and thus
                    // affecting the values of NextTransactionSize) but we haven't received the
                    // notification yet, it may happen that validAtOperationHid >
                    // latestOperationHid. In other words, nextTransactionSize may be more recent
                    // than latestOperation if we pulled it manually.

                    // We'll allow that, considering it valid. This is not ideal, but all of this
                    // will go away once the wallet uses SPV. Good enough for now.

                    if (validAtOperationHid < latestOperationHid) {
                        return null; // local value outdated
                    }

                    return transactionSize;
                });
    }

    private Optional<Operation> getLatestOperation() {
        final Operation latestOperationOrNull = operationDao.fetchLatest()
                .compose(ObservableFn.onTypedErrorResumeNext(
                        ElementNotFoundException.class,
                        error -> Observable.just(null)
                ))
                .toBlocking()
                .first();

        return Optional.ofNullable(latestOperationOrNull);
    }
}
