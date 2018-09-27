package io.muun.apollo.domain.action;

import io.muun.apollo.data.db.base.ElementNotFoundException;
import io.muun.apollo.data.db.operation.OperationDao;
import io.muun.apollo.data.db.public_profile.PublicProfileDao;
import io.muun.apollo.data.logging.Logger;
import io.muun.apollo.data.net.HoustonClient;
import io.muun.apollo.data.os.ClipboardProvider;
import io.muun.apollo.data.preferences.ExchangeRateWindowRepository;
import io.muun.apollo.data.preferences.FeeWindowRepository;
import io.muun.apollo.data.preferences.KeysRepository;
import io.muun.apollo.data.preferences.TransactionSizeRepository;
import io.muun.apollo.data.preferences.UserRepository;
import io.muun.apollo.domain.NotificationService;
import io.muun.apollo.domain.action.base.AsyncAction1;
import io.muun.apollo.domain.action.base.AsyncActionStore;
import io.muun.apollo.domain.errors.AmountTooSmallError;
import io.muun.apollo.domain.errors.InsufficientFundsError;
import io.muun.apollo.domain.errors.InvalidOperationUriError;
import io.muun.apollo.domain.errors.UserFacingError;
import io.muun.apollo.domain.model.BitcoinAmount;
import io.muun.apollo.domain.model.BitcoinUriContent;
import io.muun.apollo.domain.model.Contact;
import io.muun.apollo.domain.model.ExchangeRateWindow;
import io.muun.apollo.domain.model.FeeWindow;
import io.muun.apollo.domain.model.NextTransactionSize;
import io.muun.apollo.domain.model.Operation;
import io.muun.apollo.domain.model.OperationUri;
import io.muun.apollo.domain.model.PaymentRequest;
import io.muun.apollo.domain.model.PublicProfile;
import io.muun.apollo.domain.model.User;
import io.muun.apollo.domain.utils.FeeCalculator;
import io.muun.apollo.domain.utils.StringUtils;
import io.muun.common.Optional;
import io.muun.common.crypto.hd.MuunAddress;
import io.muun.common.crypto.hd.PublicKey;
import io.muun.common.crypto.tx.PartiallySignedTransaction;
import io.muun.common.exception.MissingCaseError;
import io.muun.common.model.ExchangeRateProvider;
import io.muun.common.model.OperationDirection;
import io.muun.common.model.OperationStatus;
import io.muun.common.rx.ObservableFn;
import io.muun.common.rx.RxHelper;
import io.muun.common.utils.BitcoinUtils;
import io.muun.common.utils.Preconditions;

import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;
import org.bitcoinj.core.Transaction;
import org.javamoney.moneta.Money;
import org.threeten.bp.ZonedDateTime;
import rx.Observable;
import rx.subjects.BehaviorSubject;

import java.math.BigDecimal;
import java.util.LinkedList;
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

    private final ContactActions contactActions;
    private final BitcoinActions bitcoinActions;
    private final SyncActions syncActions;

    private final OperationDao operationDao;
    private final PublicProfileDao publicProfileDao;

    private final KeysRepository keysRepository;
    private final UserRepository userRepository;
    private final FeeWindowRepository feeWindowRepository;
    private final ExchangeRateWindowRepository exchangeRateWindowRepository;
    private final TransactionSizeRepository transactionSizeRepository;

    private final HoustonClient houstonClient;
    private final NotificationService notificationService;
    private final ClipboardProvider clipboardProvider;

    public final AsyncAction1<OperationUri, PaymentRequest> resolveOperationUriAction;
    public final AsyncAction1<PaymentRequest, Operation> prepareOperationAction;
    public final AsyncAction1<Operation, Void> submitOperationAction;

    private BehaviorSubject<Long> balanceInSatoshisCache;

    /**
     * Constructor.
     */
    @Inject
    public OperationActions(ContactActions contactActions,
                            BitcoinActions bitcoinActions,
                            SyncActions syncActions,
                            OperationDao operationDao,
                            PublicProfileDao publicProfileDao,
                            KeysRepository keysRepository,
                            UserRepository userRepository,
                            FeeWindowRepository feeWindowRepository,
                            ExchangeRateWindowRepository exchangeRateWindowRepository,
                            TransactionSizeRepository transactionSizeRepository,
                            HoustonClient houstonClient,
                            NotificationService notificationService,
                            ClipboardProvider clipboardProvider,
                            AsyncActionStore asyncActionStore) {

        this.contactActions = contactActions;
        this.bitcoinActions = bitcoinActions;
        this.syncActions = syncActions;

        this.operationDao = operationDao;
        this.publicProfileDao = publicProfileDao;

        this.keysRepository = keysRepository;
        this.userRepository = userRepository;
        this.feeWindowRepository = feeWindowRepository;
        this.exchangeRateWindowRepository = exchangeRateWindowRepository;
        this.transactionSizeRepository = transactionSizeRepository;

        this.houstonClient = houstonClient;
        this.notificationService = notificationService;
        this.clipboardProvider = clipboardProvider;

        this.resolveOperationUriAction = asyncActionStore
                .get("operation/resolveUri", this::resolveOperationUri);

        this.prepareOperationAction = asyncActionStore
                .get("operation/prepare", this::prepareOperation);

        this.submitOperationAction = asyncActionStore
                .get("operation/submit", this::submitOperation);
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
     * Initialize an PaymentRequest using an OperationUri.
     */
    private Observable<PaymentRequest> resolveOperationUri(OperationUri uri) {
        return Observable.defer(() -> {
            switch (uri.getScheme()) {
                case "muun":
                    return Observable.just(resolveMuunUri(uri));

                case "bitcoin":
                    return Observable.just(resolveBitcoinUri(uri));

                default:
                    throw new IllegalArgumentException(uri.toString());
            }
        }).onErrorReturn(error -> {
            throw new InvalidOperationUriError(uri, error);
        });
    }

    /**
     * Build an Operation from an PaymentRequest, pre-fetching outdated information if needed.
     */
    private Observable<Operation> prepareOperation(PaymentRequest draft) {
        // We need to ensure our local data is up to date before creating an Operation from this
        // Draft. If everything is already fresh (almost always the case) these calls will return
        // immediately:
        final List<Observable<?>> preparations = new LinkedList<>();

        preparations.add(syncActions.syncRealTimeData());
        preparations.add(fetchNextTransactionSize());

        if (draft.contact != null) {
            preparations.add(contactActions.syncSingleContact(draft.contact));
        }

        return Observable.zip(preparations, RxHelper::toVoid)
                .map(ignored -> buildOperationFromDraft(draft));
    }

    /**
     * Send an Operation to Houston.
     */
    @VisibleForTesting
    public Observable<Void> submitOperation(Operation operation) {
        return Observable.defer(keysRepository::getBasePrivateKey)
                .flatMap(baseUserPrivateKey -> houstonClient.newOperation(operation)
                        .flatMap(operationCreated -> {
                            final PublicKey baseMuunPublicKey = keysRepository
                                    .getBaseMuunPublicKey();

                            // Extract data from response:
                            final Operation createdOperation = operationCreated.operation;

                            final PartiallySignedTransaction partiallySignedTransaction =
                                    operationCreated.partiallySignedTransaction;

                            final NextTransactionSize nextTransactionSize =
                                    operationCreated.nextTransactionSize;

                            partiallySignedTransaction.addUserSignatures(
                                    baseUserPrivateKey,
                                    baseMuunPublicKey
                            );

                            final Transaction fullySignedTransaction = partiallySignedTransaction
                                    .getTransaction();

                            operation.hid = createdOperation.hid;
                            operation.hash = fullySignedTransaction.getHashAsString();
                            operation.status = OperationStatus.SIGNED;

                            // Maybe Houston identified the receiver for us:
                            operation.direction = createdOperation.direction;
                            operation.receiverIsExternal = createdOperation.receiverIsExternal;
                            operation.receiverProfile = createdOperation.receiverProfile;
                            operation.receiverAddress = createdOperation.receiverAddress;
                            operation.receiverAddressDerivationPath = createdOperation
                                    .receiverAddressDerivationPath;

                            return houstonClient
                                    .pushTransaction(fullySignedTransaction, createdOperation.hid)
                                    .flatMap(ignored -> onRemoteOperationCreated(
                                            operation, nextTransactionSize
                                    ));
                        }));
    }

    /**
     * Fetch the complete operation list from Houston.
     */
    public Observable<Void> fetchReplaceOperations() {
        Logger.debug("[Operations] Fetching full operation list");

        return operationDao.deleteAll().flatMap(ignored ->
                houstonClient.fetchOperations()
                        .flatMap(Observable::from)
                        .flatMap(this::saveOperation)
                        .lastOrDefault(null)
                        .map(RxHelper::toVoid)
        );
    }

    /**
     * Watch the total balance of the wallet.
     */
    public synchronized Observable<Long> watchBalance() {
        if (balanceInSatoshisCache == null) {
            balanceInSatoshisCache = BehaviorSubject.create();

            // This subscription can last forever, as long as only one exists:
            computeBalanceFromOperationHistory().subscribe(balanceInSatoshisCache::onNext);
        }

        return balanceInSatoshisCache.asObservable();
    }

    /**
     * Watch the maximum spendable (balance - fee) amount, updating on fee/transactionSize changes.
     */
    public Observable<Long> watchMaxSpendableAmount() {
        return Observable.combineLatest(
                feeWindowRepository.fetch(),
                transactionSizeRepository.watchNextTransactionSize(),
                this::getMaxSpendableAmount
        );
    }

    private long getMaxSpendableAmount(FeeWindow feeWindow, NextTransactionSize transactionSize) {
        Preconditions.checkNotNull(feeWindow);
        Preconditions.checkNotNull(transactionSize);

        return new FeeCalculator(feeWindow.feeInSatoshisPerByte, transactionSize.sizeProgression)
                .getMaxSpendableAmount();
    }

    private Observable<Long> computeBalanceFromOperationHistory() {
        return operationDao.fetchAll().map(operations -> {
            long amount = 0L;

            for (Operation operation : operations) {
                if (!operation.isFailed()) {
                    amount += operation.getBalanceInSatoshisForUser();
                }
            }

            return amount;
        });
    }

    /**
     * Returns a new Observable that returns the current fee on `onNext` or some exception
     * on `onError` (i.e.: InsufficientFundsError, AmountTooSmallError).
     *
     * @return an observable for the fee, if it was able to calculate it.
     */
    public Observable<UserFacingError> verifyCanSpendObs(MonetaryAmount monetaryAmount) {

        final Observable<Long> balance = watchBalance().first();

        final Observable<NextTransactionSize> nextTxSize = watchValidNextTransactionSize().first();

        final CurrencyUnit btc = Monetary.getCurrency("BTC");
        final Observable<Long> amount = transformCurrency(monetaryAmount, btc)
                .map(BitcoinUtils::bitcoinsToSatoshis)
                .first();

        return Observable.zip(balance, amount, nextTxSize, this::verifySufficientAmount);
    }

    @Nullable
    private UserFacingError verifySufficientAmount(Long balance,
                                                   Long amount,
                                                   NextTransactionSize size)
            throws InsufficientFundsError, AmountTooSmallError {

        if (balance < amount) {
            return new InsufficientFundsError();
        }

        final FeeWindow feeWindow = feeWindowRepository.fetchOne();
        final FeeCalculator feeCalculator = new FeeCalculator(
                feeWindow.feeInSatoshisPerByte,
                size.sizeProgression
        );

        final long feeForAmount;

        try {
            feeForAmount = feeCalculator.getFeeForAmount(amount);
        } catch (UserFacingError e) {
            return e;
        }

        if (balance < feeForAmount + amount) {
            return new InsufficientFundsError();
        }

        return null; // No errors found.
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

    /**
     * Fetch and store the size estimation for the next transaction, if necessary.
     */
    public Observable<Void> fetchNextTransactionSize() {
        return watchValidNextTransactionSize()
                .first()
                .flatMap(transactionSize -> {
                    if (transactionSize != null) {
                        return Observable.just(null); // we don't need to re-fetch
                    }

                    return houstonClient.fetchNextTransactionSize()
                            .doOnNext(this::onNextTransactionSizeUpdated)
                            .map(RxHelper::toVoid);
                });
    }

    /**
     * Invoked when a new transaction size estimation is reported by Houston.
     */
    public void onNextTransactionSizeUpdated(NextTransactionSize nextTransactionSize) {
        Logger.debug("Updating next transaction size estimation");
        transactionSizeRepository.setTransactionSize(nextTransactionSize);
    }

    /**
     * Invoked when a new Operation is reported by Houston.
     */
    public Observable<Void> onRemoteOperationCreated(Operation operation,
                                                     NextTransactionSize nextTransactionSize) {
        return saveOperation(operation)
                .doOnNext(savedOperation -> {
                    onNextTransactionSizeUpdated(nextTransactionSize);

                    if (savedOperation.direction == OperationDirection.INCOMING) {
                        notificationService.showNewOperationNotification(savedOperation);
                    }
                })
                .map(RxHelper::toVoid);
    }

    /**
     * Invoked when an Operation update is reported by Houston.
     */
    public Observable<Void> onRemoteOperationUpdated(long hid,
                                                     long confirmations,
                                                     String hash,
                                                     OperationStatus status) {

        // TODO: show a push notification if the operation was dropped

        operationDao.updateStatus(hid, confirmations, hash, status);

        // TODO: remove this quick fix
        return Observable.just(null);
    }


    // ---------------------------------------------------------------------------------------------
    // Private helpers

    private PaymentRequest resolveMuunUri(OperationUri uri) {
        final User user = userRepository.fetchOne();

        final String amountParam = uri.getParam(OperationUri.MUUN_AMOUNT)
                .orElse("0");

        final String currencyParam = uri.getParam(OperationUri.MUUN_CURRENCY)
                .orElse(user.primaryCurrency.getCurrencyCode());

        final String descriptionParam = uri.getParam(OperationUri.MUUN_DESCRIPTION)
                .orElse("");

        final MonetaryAmount amount = Money.of(new BigDecimal(amountParam), currencyParam);

        switch (uri.getHost()) {
            case OperationUri.MUUN_HOST_CONTACT:
                final Contact contact = contactActions
                        .fetchContact(Long.parseLong(uri.getPath()))
                        .toBlocking()
                        .first();

                return PaymentRequest.toContact(contact, amount, descriptionParam);

            case OperationUri.MUUN_HOST_EXTERNAL:
                return PaymentRequest.toAddress(uri.getPath(), amount, descriptionParam);

            default:
                throw new IllegalArgumentException("Invalid host: " + uri.getHost());
        }
    }

    private PaymentRequest resolveBitcoinUri(OperationUri uri) {
        final User user = userRepository.fetchOne();

        final BitcoinUriContent uriContent = bitcoinActions
                .getBitcoinUriContent(uri.toString())
                .toBlocking()
                .first();

        final MonetaryAmount amount = (uriContent.amountInStatoshis != null)
                ? BitcoinUtils.satoshisToBitcoins(uriContent.amountInStatoshis)
                : Money.of(0, user.primaryCurrency);


        final String description = StringUtils.joinText(": ", new String[]{
                uriContent.merchant,
                uriContent.memo
        });

        return PaymentRequest.toAddress(uriContent.address, amount, description);
    }

    Operation buildOperationFromDraft(PaymentRequest draft) {

        switch (draft.type) {
            case TO_CONTACT:
                final MuunAddress muunAddress = contactActions
                        .getAddressForContact(draft.contact);

                return buildOperationFromData(
                        publicProfileDao.fetchOneByHid(draft.contact.hid),
                        muunAddress.getAddress(),
                        muunAddress.getDerivationPath(),
                        draft.amount,
                        draft.description
                );

            case TO_ADDRESS:
                return buildOperationFromData(
                        null,
                        draft.address,
                        null,
                        draft.amount,
                        draft.description
                );

            default:
                throw new MissingCaseError(draft.type);
        }
    }

    private Operation buildOperationFromData(PublicProfile contactProfile,
                                             String address,
                                             String addressDerivationPath,
                                             MonetaryAmount inputAmount,
                                             String description) {

        final User user = userRepository.fetchOne();

        final ExchangeRateWindow rateWindow = exchangeRateWindowRepository.fetchOne();
        final ExchangeRateProvider rates = new ExchangeRateProvider(rateWindow.rates);

        final CurrencyConversion toBtc = rates.getCurrencyConversion("BTC");
        final CurrencyConversion toInput = rates.getCurrencyConversion(inputAmount.getCurrency());
        final CurrencyConversion toPrimary = rates.getCurrencyConversion(user.primaryCurrency);

        final long inputAmountInSatoshis = BitcoinUtils.bitcoinsToSatoshis(inputAmount.with(toBtc));
        final Long feeInSatoshis = getFeeForAmount(inputAmountInSatoshis);

        return new Operation(
                null,
                -1L, // we won't have the operation hid until houston's response
                null,
                contactProfile == null,
                user.getCompatPublicProfile(),
                false,
                contactProfile,
                contactProfile == null,
                address,
                addressDerivationPath,
                new BitcoinAmount(
                        inputAmountInSatoshis,
                        inputAmount,
                        inputAmount.with(toPrimary)
                ),
                new BitcoinAmount(
                        feeInSatoshis,
                        BitcoinUtils.satoshisToBitcoins(feeInSatoshis).with(toInput),
                        BitcoinUtils.satoshisToBitcoins(feeInSatoshis).with(toPrimary)
                ),
                0L,
                null, // we won't have the hash until the tx is signed
                description,
                OperationStatus.CREATED,
                ZonedDateTime.now(),
                rateWindow.windowHid
        );
    }

    /**
     * Return the suggested transaction fee to transfer a given amount.
     */
    public long getFeeForAmount(long operationAmount) throws InsufficientFundsError {
        final FeeWindow feeWindow = feeWindowRepository.fetchOne();

        final NextTransactionSize transactionSize = watchValidNextTransactionSize()
                .toBlocking()
                .first();

        Preconditions.checkNotNull(transactionSize);

        final FeeCalculator feeCalculator = new FeeCalculator(
                feeWindow.feeInSatoshisPerByte,
                transactionSize.sizeProgression
        );

        return feeCalculator.getFeeForAmount(operationAmount);
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

    private Observable<Operation> saveOperation(Operation operation) {
        return operationDao.fetchByHid(operation.hid)
                .first()
                .compose(ObservableFn.onTypedErrorResumeNext(
                        ElementNotFoundException.class,
                        error -> {
                            Observable<Operation> chain = Observable.just(operation);

                            if (operation.senderProfile != null) {
                                chain = chain.compose(ObservableFn.flatDoOnNext(ignored ->
                                        publicProfileDao.store(operation.senderProfile)
                                ));
                            }

                            if (operation.receiverProfile != null) {
                                chain = chain.compose(ObservableFn.flatDoOnNext(ignored ->
                                        publicProfileDao.store(operation.receiverProfile)
                                ));
                            }

                            chain = chain.flatMap(operationDao::store);

                            return chain;
                        }
                ));
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
