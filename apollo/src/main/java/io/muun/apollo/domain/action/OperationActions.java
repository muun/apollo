package io.muun.apollo.domain.action;

import io.muun.apollo.data.db.base.ElementNotFoundException;
import io.muun.apollo.data.db.operation.OperationDao;
import io.muun.apollo.data.logging.Logger;
import io.muun.apollo.data.net.HoustonClient;
import io.muun.apollo.data.os.ClipboardProvider;
import io.muun.apollo.data.preferences.ExchangeRateWindowRepository;
import io.muun.apollo.data.preferences.FeeWindowRepository;
import io.muun.apollo.data.preferences.TransactionSizeRepository;
import io.muun.apollo.data.preferences.UserRepository;
import io.muun.apollo.domain.action.base.AsyncAction2;
import io.muun.apollo.domain.action.base.AsyncActionStore;
import io.muun.apollo.domain.action.operation.CreateOperationAction;
import io.muun.apollo.domain.action.operation.SubmitIncomingPaymentAction;
import io.muun.apollo.domain.action.operation.SubmitOutgoingPaymentAction;
import io.muun.apollo.domain.model.NextTransactionSize;
import io.muun.apollo.domain.model.Operation;
import io.muun.apollo.domain.model.OperationUri;
import io.muun.apollo.domain.model.PendingWithdrawal;
import io.muun.common.Optional;
import io.muun.common.crypto.hd.HardwareWalletOutput;
import io.muun.common.crypto.hd.MuunAddress;
import io.muun.common.rx.ObservableFn;
import io.muun.common.rx.RxHelper;

import rx.Observable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javax.inject.Inject;
import javax.inject.Singleton;


@Singleton
public class OperationActions {

    private final CreateOperationAction createOperation;
    private final SubmitOutgoingPaymentAction submitOutgoingPayment;
    private final SubmitIncomingPaymentAction submitIncomingPayment;

    private final HardwareWalletActions hardwareWalletActions;
    private final SatelliteActions satelliteActions;
    private final AddressActions addressActions;

    private final OperationDao operationDao;

    private final UserRepository userRepository;
    private final FeeWindowRepository feeWindowRepository;
    private final ExchangeRateWindowRepository exchangeRateWindowRepository;
    private final TransactionSizeRepository transactionSizeRepository;

    private final HoustonClient houstonClient;
    private final ClipboardProvider clipboardProvider;

    public final AsyncAction2<String, String, Void> submitSignedWithdrawalAction;

    /**
     * Constructor.
     */
    @Inject
    public OperationActions(CreateOperationAction createOperation,
                            SubmitOutgoingPaymentAction submitOutgoingPayment,
                            SubmitIncomingPaymentAction submitIncomingPayment,
                            HardwareWalletActions hardwareWalletActions,
                            SatelliteActions satelliteActions,
                            AddressActions addressActions,
                            OperationDao operationDao,
                            UserRepository userRepository,
                            FeeWindowRepository feeWindowRepository,
                            ExchangeRateWindowRepository exchangeRateWindowRepository,
                            TransactionSizeRepository transactionSizeRepository,
                            HoustonClient houstonClient,
                            ClipboardProvider clipboardProvider,
                            AsyncActionStore asyncActionStore) {

        this.createOperation = createOperation;
        this.submitOutgoingPayment = submitOutgoingPayment;
        this.submitIncomingPayment = submitIncomingPayment;
        this.hardwareWalletActions = hardwareWalletActions;
        this.satelliteActions = satelliteActions;
        this.addressActions = addressActions;

        this.operationDao = operationDao;

        this.userRepository = userRepository;
        this.feeWindowRepository = feeWindowRepository;
        this.exchangeRateWindowRepository = exchangeRateWindowRepository;
        this.transactionSizeRepository = transactionSizeRepository;

        this.houstonClient = houstonClient;
        this.clipboardProvider = clipboardProvider;

        this.submitSignedWithdrawalAction = asyncActionStore
                .get("operation/submit-signed-withdrawal", this::submitSignedWithdrawal);
    }

    /**
     * Return an OperationUri present in the system clipboard, if any.
     */
    public Observable<Optional<OperationUri>> watchClipboardForUris() {
        return clipboardProvider.watchPrimaryClip().map(content -> {
            if (Objects.equals(content, userRepository.getLastCopiedAddress())) {
                return Optional.empty(); // don't show the user the address he just generated
            }

            try {
                return Optional.of(OperationUri.fromString(content));

            } catch (IllegalArgumentException ex) {
                return Optional.empty(); // not an URI or address
            }
        });
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

    private Observable<Void> submitSignedWithdrawal(String uuid, String signedTransaction) {
        return satelliteActions.watchPendingWithdrawal()
                .first()
                .flatMap(maybePendingWithdrawal -> {
                    Logger.debug("[Operations] Submitting signed withdrawal");

                    if (! maybePendingWithdrawal.isPresent()) {
                        Logger.debug("[Operations] No pending withdrawal present, ignoring");
                        return Observable.just(null);
                    }

                    final PendingWithdrawal pendingWithdrawal = maybePendingWithdrawal.get();

                    if (! pendingWithdrawal.uuid.equals(uuid)) {
                        Logger.debug("[Operations] Signed withdrawal with wrong UUID, ignoring");
                        return Observable.just(null);
                    }

                    pendingWithdrawal.signedSerializedTransaction = signedTransaction;

                    final List<HardwareWalletOutput> spentOutputs = hardwareWalletActions
                            .buildWithdrawal(pendingWithdrawal)
                            .getInputs();

                    final List<Long> inputAmounts = new ArrayList<>();
                    for (HardwareWalletOutput spentOutput : spentOutputs) {
                        inputAmounts.add(spentOutput.getAmount());
                    }

                    return houstonClient
                            .newWithdrawalOperation(
                                    buildOperationFromPendingWithdrawal(pendingWithdrawal),
                                    signedTransaction,
                                    inputAmounts
                            )
                            .flatMap(operationCreated -> createOperation.action(
                                    operationCreated.operation,
                                    operationCreated.nextTransactionSize
                            ))
                            .flatMap(res -> satelliteActions.endWithdrawal(pendingWithdrawal))
                            .doOnError(error -> {
                                Logger.debug("[Operations] Error submitting signed withdrawal");
                                // TODO notify Satellite about this failure.
                            });
                });
    }

    /**
     * Fetch the complete operation list from Houston.
     */
    public Observable<Void> fetchReplaceOperations() {
        Logger.debug("[Operations] Fetching full operation list");

        return operationDao.deleteAll().flatMap(ignored ->
                houstonClient.fetchOperations()
                        .flatMap(Observable::from)
                        .flatMap(createOperation::saveOperation)
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

    /**
     * Fetch the (extended) operation list form the database.
     */
    public Observable<List<Operation>> fetchOperations() {

        return operationDao.fetchAll();
    }

    // ---------------------------------------------------------------------------------------------
    // Private helpers

    private Operation buildOperationFromPendingWithdrawal(PendingWithdrawal withdrawal) {
        final MuunAddress address = addressActions.getExternalMuunAddress();

        return Operation.createIncoming(
                userRepository.fetchOne().getCompatPublicProfile(),
                withdrawal.hardwareWalletHid,
                address.getAddress(),
                address.getDerivationPath(),
                withdrawal.amount,
                withdrawal.fee,
                withdrawal.description,
                withdrawal.exchangeRateWindowHid
        );
    }

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
                            .map(latestOperation -> latestOperation.getHid())
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
