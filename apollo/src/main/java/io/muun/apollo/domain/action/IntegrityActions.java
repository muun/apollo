package io.muun.apollo.domain.action;


import io.muun.apollo.data.net.ApiObjectsMapper;
import io.muun.apollo.data.net.HoustonClient;
import io.muun.apollo.data.preferences.AuthRepository;
import io.muun.apollo.data.preferences.KeysRepository;
import io.muun.apollo.data.preferences.UserRepository;
import io.muun.apollo.data.serialization.SerializationUtils;
import io.muun.apollo.domain.errors.AddressRecordIntegrityError;
import io.muun.apollo.domain.errors.BalanceIntegrityError;
import io.muun.apollo.domain.errors.IntegrityError;
import io.muun.apollo.domain.errors.PublicKeySetIntegrityError;
import io.muun.common.api.IntegrityCheck;
import io.muun.common.api.IntegrityStatus;
import io.muun.common.api.PublicKeySetJson;
import io.muun.common.crypto.hd.PublicKey;
import io.muun.common.model.SessionStatus;

import android.util.Log;
import com.crashlytics.android.Crashlytics;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import rx.Observable;

import javax.inject.Inject;

public class IntegrityActions {

    private final OperationActions operationActions;

    private final KeysRepository keysRepository;
    private final AuthRepository authRepository;
    private final UserRepository userRepository;

    private final HoustonClient houstonClient;

    private final ApiObjectsMapper apiObjectsMapper;

    /**
     * Construct this class.
     */
    @Inject
    public IntegrityActions(OperationActions operationActions,
                            KeysRepository keysRepository,
                            AuthRepository authRepository,
                            UserRepository userRepository,
                            HoustonClient houstonClient,
                            ApiObjectsMapper apiObjectsMapper) {

        this.operationActions = operationActions;
        this.keysRepository = keysRepository;
        this.authRepository = authRepository;
        this.userRepository = userRepository;
        this.houstonClient = houstonClient;
        this.apiObjectsMapper = apiObjectsMapper;
    }

    /**
     * Perform an integrity check of the local data, comparing it with Houson's information. Log
     * to Crahslytics if inconsistencies are found.
     */
    public Observable<Void> checkIntegrity() {
        final PublicKey basePublicKey = keysRepository.getBasePublicKey();
        final Integer externalMaxUsedIndex = keysRepository.getMaxUsedExternalAddressIndex();

        // Watch balance is a "potentially" never ending observable, checkIntegrity is a one time
        // deal (and should return a Single) run in background and we DON'T want never-ending stuff
        // running on background. So, we use first().
        // TODO use Single
        return operationActions.watchBalance().first().flatMap(balanceInSatoshis ->
            checkIntegrity(
                    basePublicKey,
                    externalMaxUsedIndex,
                    balanceInSatoshis
            )
        );
    }

    private Observable<Void> checkIntegrity(final PublicKey basePublicKey,
                                            final Integer externalMaxUsedIndex,
                                            final Long balanceInSatoshis) {

        final SessionStatus sessionStatus = authRepository.getSessionStatus().orElse(null);
        final boolean hasNotReachedHome = userRepository.hasSignupDraft();

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
                .flatMap(status -> handleIntegrityStatus(integrityCheck, status));
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

            Log.e("Integrity", "Check failed", error);
            Crashlytics.logException(error);
        }

        return Observable.just(null);
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
