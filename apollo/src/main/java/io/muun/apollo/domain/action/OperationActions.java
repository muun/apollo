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
import io.muun.apollo.domain.errors.AmountTooSmallError;
import io.muun.apollo.domain.errors.InsufficientFundsError;
import io.muun.apollo.domain.errors.UserFacingError;
import io.muun.apollo.domain.model.NextTransactionSize;
import io.muun.apollo.domain.model.Operation;
import io.muun.apollo.domain.model.OperationUri;
import io.muun.apollo.domain.model.PaymentRequest;
import io.muun.apollo.domain.model.PendingWithdrawal;
import io.muun.apollo.domain.model.PreparedPayment;
import io.muun.apollo.domain.utils.FeeCalculator;
import io.muun.common.Optional;
import io.muun.common.crypto.hd.HardwareWalletOutput;
import io.muun.common.crypto.hd.MuunAddress;
import io.muun.common.crypto.hwallet.HardwareWalletState;
import io.muun.common.model.ExchangeRateProvider;
import io.muun.common.model.SizeForAmount;
import io.muun.common.rx.ObservableFn;
import io.muun.common.rx.RxHelper;
import io.muun.common.utils.BitcoinUtils;
import io.muun.common.utils.Preconditions;

import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;
import rx.Observable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.money.CurrencyUnit;
import javax.money.Monetary;
import javax.money.MonetaryAmount;
import javax.money.convert.CurrencyConversion;


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

    /**
     * Send an Operation to Houston.
     */
    @VisibleForTesting
    public Observable<Void> submitPayment(PaymentRequest payReq, PreparedPayment prepPayment) {

        // Mmmh, this is not very elegant, or well named.
        if (payReq.type == PaymentRequest.Type.FROM_HARDWARE_WALLET) {
            return submitIncomingPayment.action(payReq, prepPayment);

        } else {
            return submitOutgoingPayment.action(payReq, prepPayment);
        }
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
     * Return an Observable of null if the PaymentRequest can be paid with current balance, or an
     * error if not (either AmountTooSmall or InsufficientFunds).
     */
    public Observable<UserFacingError> verifyCanPay(PaymentRequest payReq) {
        Preconditions.checkNotNull(payReq);
        Preconditions.checkNotNull(payReq.amount);

        return Observable.zip(
                transformToSatoshis(payReq.amount),
                watchMaxSpendableAmount(payReq),

                (amount, maxSpendableAmount) -> {

                    // For submarine swaps invoice amount can be < DUST. What it can't be
                    // is the total swap/funding transaction output amount.
                    if (payReq.swap != null ) {
                        amount = payReq.swap.fundingOutput.outputAmountInSatoshis;
                    }

                    return validateAmount(amount, maxSpendableAmount);
                }
        ).first();
    }

    @Nullable
    private UserFacingError validateAmount(Long amount, Long maxSpendableAmount) {
        if (amount < BitcoinUtils.DUST_IN_SATOSHIS) {
            return new AmountTooSmallError();
        }

        if (amount > maxSpendableAmount) {
            return new InsufficientFundsError();
        }

        return null;
    }

    /**
     * Watch the maximum spendable (balance - fee) amount, updating on fee/transactionSize changes.
     */
    public Observable<Long> watchMaxSpendableAmount(PaymentRequest payReq) {
        return Observable.combineLatest(
                feeWindowRepository.fetch(),
                watchSizeProgression(payReq),

                (feeWindow, sizeProgression) -> {
                    Preconditions.checkNotNull(feeWindow);
                    Preconditions.checkNotNull(sizeProgression);

                    return new FeeCalculator(feeWindow.feeInSatoshisPerByte, sizeProgression)
                            .getMaxSpendableAmount();
                }
        );
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

    private Observable<List<SizeForAmount>> watchSizeProgression(PaymentRequest payReq) {
        if (payReq.type == PaymentRequest.Type.FROM_HARDWARE_WALLET) {
            final HardwareWalletState state = hardwareWalletActions
                    .getHardwareWalletState(payReq.hardwareWallet.hid);

            // NOTE: nothing is being watched here, we just assume it's always fresh:
            return Observable.just(state.getSizeForAmounts());

        } else {
            return watchValidNextTransactionSize().map(t -> t.sizeProgression);
        }
    }

    private Observable<Long> transformToSatoshis(MonetaryAmount amount) {
        return transformCurrency(amount, Monetary.getCurrency("BTC"))
                .map(BitcoinUtils::bitcoinsToSatoshis);
    }

    private Observable<MonetaryAmount> transformCurrency(MonetaryAmount amount,
                                                         CurrencyUnit targetCurrency) {
        return exchangeRateWindowRepository.fetch()
                .map(rateWindow -> {

                    final CurrencyConversion toCurrency =
                            new ExchangeRateProvider(rateWindow.rates)
                                    .getCurrencyConversion(targetCurrency);

                    return amount.with(toCurrency);
                });
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
                            .map(latestOperation -> latestOperation.hid)
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
