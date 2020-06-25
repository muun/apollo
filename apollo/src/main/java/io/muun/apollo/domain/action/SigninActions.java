package io.muun.apollo.domain.action;

import io.muun.apollo.data.logging.LoggingContext;
import io.muun.apollo.data.net.HoustonClient;
import io.muun.apollo.data.preferences.AuthRepository;
import io.muun.apollo.data.preferences.KeysRepository;
import io.muun.apollo.data.preferences.UserRepository;
import io.muun.apollo.domain.action.base.AsyncAction2;
import io.muun.apollo.domain.action.base.AsyncActionStore;
import io.muun.apollo.domain.action.keys.CreateChallengeSetupAction;
import io.muun.apollo.domain.action.keys.StoreChallengeKeyAction;
import io.muun.apollo.domain.model.SignupDraft;
import io.muun.apollo.domain.model.User;
import io.muun.common.Optional;
import io.muun.common.api.SetupChallengeResponse;
import io.muun.common.crypto.ChallengeType;
import io.muun.common.model.SessionStatus;

import androidx.core.util.Pair;
import rx.Completable;
import rx.Observable;

import javax.inject.Inject;
import javax.inject.Singleton;


@Singleton
public class SigninActions {

    private final AuthRepository authRepository;
    private final UserRepository userRepository;
    private final KeysRepository keysRepository;

    private final HoustonClient houstonClient;

    public final AsyncAction2<ChallengeType, String, SetupChallengeResponse> updateChallengeSetup;

    private final CreateChallengeSetupAction createChallengeSetup;
    private final StoreChallengeKeyAction storeChallengeKey;

    /**
     * Constructor.
     */
    @Inject
    public SigninActions(AsyncActionStore asyncActionStore,
                         AuthRepository authRepository,
                         UserRepository userRepository,
                         HoustonClient houstonClient,
                         KeysRepository keysRepository,
                         CreateChallengeSetupAction createChallengeSetup,
                         StoreChallengeKeyAction storeChallengeKey) {

        this.authRepository = authRepository;
        this.houstonClient = houstonClient;
        this.userRepository = userRepository;
        this.keysRepository = keysRepository;

        this.updateChallengeSetup =
                asyncActionStore.get("challenge/update", this::setupChallenge);

        this.createChallengeSetup = createChallengeSetup;
        this.storeChallengeKey = storeChallengeKey;
    }

    /**
     * Setups Crashlytics identifiers.
     */
    public void setupCrashlytics() {
        final User user = userRepository.fetchOne();

        if (user.email.isPresent()) {
            LoggingContext.configure(user.email.get(), user.hid.toString());
        }
    }

    public Optional<SessionStatus> getSessionStatus() {
        return authRepository.getSessionStatus();
    }

    public void clearSession() {
        authRepository.clear();
    }

    public void reportAuthorizedByEmail() {
        authRepository.storeSessionStatus(SessionStatus.AUTHORIZED_BY_EMAIL);
    }

    /**
     * Watch for the user to confirm session.
     */
    public Completable awaitAuthorizedByEmail() {
        return authRepository.awaitAuthorizedByEmail();
    }

    /**
     * Set up a challenge and give the public key to Houston.
     */
    public Observable<SetupChallengeResponse> setupChallenge(ChallengeType challengeType,
                                                             String userInput) {

        return createChallengeSetup.action(challengeType, userInput)
                .flatMap(houstonClient::setupChallenge, Pair::new)
                .doOnNext(pair -> {
                    storeChallengeKey.actionNow(pair.first.type, pair.first.publicKey);

                    if (pair.second.muunKey != null) {
                        keysRepository.storeEncryptedMuunPrivateKey(pair.second.muunKey);
                    }

                    if (challengeType == ChallengeType.RECOVERY_CODE) {
                        userRepository.setRecoveryCodeSetupInProcess(false);
                    }
                })
                .map(pair -> pair.second);
    }

    public Optional<SignupDraft> fetchSignupDraft() {
        return userRepository.fetchSignupDraft();
    }

    public void saveSignupDraft(SignupDraft signupDraft) {
        userRepository.storeSignupDraft(signupDraft);
    }

    public void clearSignupDraft() {
        userRepository.storeSignupDraft(null);
    }
}
