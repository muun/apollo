package io.muun.apollo.domain.action.integrity;


import io.muun.apollo.data.db.base.ElementNotFoundException;
import io.muun.apollo.data.db.operation.OperationDao;
import io.muun.apollo.data.net.ApiObjectsMapper;
import io.muun.apollo.data.net.HoustonClient;
import io.muun.apollo.data.os.execution.ExecutionTransformerFactory;
import io.muun.apollo.data.preferences.AuthRepository;
import io.muun.apollo.data.preferences.KeysRepository;
import io.muun.apollo.data.preferences.TransactionSizeRepository;
import io.muun.apollo.data.preferences.UserRepository;
import io.muun.apollo.data.serialization.SerializationUtils;
import io.muun.apollo.domain.errors.integrity.AddressRecordIntegrityError;
import io.muun.apollo.domain.errors.integrity.BalanceIntegrityError;
import io.muun.apollo.domain.errors.integrity.IntegrityError;
import io.muun.apollo.domain.errors.integrity.PublicKeySetIntegrityError;
import io.muun.apollo.domain.model.NextTransactionSize;
import io.muun.apollo.domain.model.Operation;
import io.muun.common.Optional;
import io.muun.common.api.IntegrityCheck;
import io.muun.common.api.IntegrityStatus;
import io.muun.common.api.PublicKeySetJson;
import io.muun.common.crypto.hd.PublicKey;
import io.muun.common.model.SessionStatus;
import io.muun.common.rx.ObservableFn;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import rx.Observable;
import timber.log.Timber;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Although this is a single "action", there's no need to extend BaseAsyncAction since its only
 * usage does not warrant the complex behaviour that BaseAsyncAction provides/handles.
 */
@Singleton
public class IntegrityAction {

    private final KeysRepository keysRepository;
    private final AuthRepository authRepository;
    private final UserRepository userRepository;
    private final TransactionSizeRepository transactionSizeRepository;

    private final OperationDao operationDao;

    private final HoustonClient houstonClient;

    private final ApiObjectsMapper apiObjectsMapper;

    private final ExecutionTransformerFactory transformerFactory;

    /**
     * Construct this class.
     */
    @Inject
    public IntegrityAction(KeysRepository keysRepository,
                           AuthRepository authRepository,
                           UserRepository userRepository,
                           TransactionSizeRepository transactionSizeRepository,
                           OperationDao operationDao,
                           HoustonClient houstonClient,
                           ApiObjectsMapper apiObjectsMapper,
                           ExecutionTransformerFactory transformerFactory) {

        this.keysRepository = keysRepository;
        this.authRepository = authRepository;
        this.userRepository = userRepository;
        this.transactionSizeRepository = transactionSizeRepository;
        this.operationDao = operationDao;
        this.houstonClient = houstonClient;
        this.apiObjectsMapper = apiObjectsMapper;
        this.transformerFactory = transformerFactory;
    }

    /**
     * Perform an integrity check of the local data, comparing it with Houston's information. Log
     * to Crashlytics if inconsistencies are found.
     */
    public Observable<Void> checkIntegrity() {
        final PublicKey basePublicKey = keysRepository.getBasePublicKey();
        final Integer externalMaxUsedIndex = keysRepository.getMaxUsedExternalAddressIndex();

        // Watch balance is a "potentially" never ending observable, checkIntegrity is a one time
        // deal (and should return a Single) run in background and we DON'T want never-ending stuff
        // running on background. So, we use first().
        // TODO use Single
        return watchBalance().first().flatMap(balanceInSatoshis ->
            checkIntegrity(
                    basePublicKey,
                    externalMaxUsedIndex,
                    balanceInSatoshis
            )
        );
    }

    // ---------------------------------------------------------------------------------------------
    // Private helpers

    private Observable<Void> checkIntegrity(final PublicKey basePublicKey,
                                            final Integer externalMaxUsedIndex,
                                            final Long balanceInSatoshis) {

        final SessionStatus sessionStatus = authRepository.getSessionStatus().orElse(null);
        final boolean hasNotReachedHome = !userRepository.isInitialSyncCompleted();

        if (sessionStatus != SessionStatus.LOGGED_IN || hasNotReachedHome) {
            return Observable.just(null);
        }

        // NOTE: using the ApiObjectsMapper here is not exactly kosher, but we'd like to log
        // to Crashlytics the exact integrity check made to Houston.

        // Perhaps this could be at the Client layer, but after some consideration, I thought
        // it would be best to keep the code here. I realize that this is a subjective choice with
        // only convenience for a basis, so I hereby authorize you, the Reader[1], to refactor this
        // piece on a future occasion.

        final PublicKeySetJson publicKeySet = new PublicKeySetJson(
                apiObjectsMapper.mapPublicKey(basePublicKey),
                apiObjectsMapper.mapExternalAddressesRecord(externalMaxUsedIndex)
        );

        final IntegrityCheck integrityCheck = new IntegrityCheck(publicKeySet, balanceInSatoshis);

        return houstonClient.checkIntegrity(integrityCheck)
                .flatMap(status -> handleIntegrityStatus(integrityCheck, status))
                .subscribeOn(transformerFactory.getBackgroundScheduler());
    }

    private Observable<Void> handleIntegrityStatus(IntegrityCheck integrityCheck,
                                                   IntegrityStatus integrityStatus) {

        if (!integrityStatus.isOk()) {
            final String message = new IntegrityFailureMessage(integrityCheck, integrityStatus)
                    .toString();

            final Throwable error;

            if (! integrityStatus.isBalanceOk) {
                error = new BalanceIntegrityError(message);

            } else if (! integrityStatus.isBasePublicKeyOk) {
                error = new PublicKeySetIntegrityError(message);

            } else if (! integrityStatus.isExternalMaxUsedIndexOk) {
                error = new AddressRecordIntegrityError(message);

            } else {
                error = new IntegrityError(message);
            }

            Timber.e(error);
        }

        return Observable.just(null);
    }

    /**
     * Watch the total balance of the wallet.
     */
    private Observable<Long> watchBalance() {
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

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class IntegrityFailureMessage {
        public IntegrityCheck check;
        public IntegrityStatus status;

        public IntegrityFailureMessage(IntegrityCheck check, IntegrityStatus status) {
            this.check = check;
            this.status = status;
        }

        @Override
        public String toString() {
            return SerializationUtils.serializeJson(IntegrityFailureMessage.class, this);
        }
    }
}
