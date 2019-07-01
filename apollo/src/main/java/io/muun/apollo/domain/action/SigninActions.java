package io.muun.apollo.domain.action;

import io.muun.apollo.data.logging.Logger;
import io.muun.apollo.data.net.HoustonClient;
import io.muun.apollo.data.os.execution.ExecutionTransformerFactory;
import io.muun.apollo.data.preferences.AuthRepository;
import io.muun.apollo.data.preferences.KeysRepository;
import io.muun.apollo.data.preferences.UserRepository;
import io.muun.apollo.domain.action.base.AsyncAction1;
import io.muun.apollo.domain.action.base.AsyncAction2;
import io.muun.apollo.domain.action.base.AsyncActionStore;
import io.muun.apollo.domain.action.operation.FetchNextTransactionSizeAction;
import io.muun.apollo.domain.action.realtime.FetchRealTimeDataAction;
import io.muun.apollo.domain.errors.FcmTokenNotAvailableError;
import io.muun.apollo.domain.errors.InitialSyncError;
import io.muun.apollo.domain.errors.InvalidChallengeSignatureError;
import io.muun.apollo.domain.errors.PasswordIntegrityError;
import io.muun.apollo.domain.model.SignupDraft;
import io.muun.apollo.domain.model.User;
import io.muun.apollo.external.Globals;
import io.muun.common.Optional;
import io.muun.common.api.KeySet;
import io.muun.common.api.SetupChallengeResponse;
import io.muun.common.crypto.ChallengePrivateKey;
import io.muun.common.crypto.ChallengePublicKey;
import io.muun.common.crypto.ChallengeType;
import io.muun.common.model.CreateSessionOk;
import io.muun.common.model.SessionStatus;
import io.muun.common.model.challenge.Challenge;
import io.muun.common.model.challenge.ChallengeSignature;
import io.muun.common.rx.ObservableFn;
import io.muun.common.rx.RxHelper;

import android.support.annotation.VisibleForTesting;
import android.support.v4.util.Pair;
import rx.Completable;
import rx.Observable;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.money.CurrencyUnit;
import javax.validation.constraints.NotNull;


@Singleton
public class SigninActions {

    private final AuthRepository authRepository;
    private final UserRepository userRepository;
    private final KeysRepository keysRepository;

    private final HoustonClient houstonClient;

    private final CurrencyActions currencyActions;
    private final ContactActions contactActions;
    private final OperationActions operationActions;
    private final AddressActions addressActions;
    private final UserActions userActions;
    private final HardwareWalletActions hardwareWalletActions;

    private final FetchNextTransactionSizeAction fetchNextTransactionSize;
    private final FetchRealTimeDataAction fetchRealTimeData;

    private final ExecutionTransformerFactory transformerFactory;

    public final AsyncAction1<String, CreateSessionOk> createSessionAction;
    public final AsyncAction2<ChallengeType, String, SetupChallengeResponse>
            updateChallengeSetupAction;
    public final AsyncAction1<SignupDraft, Void> signupAction;
    public final AsyncAction2<ChallengeType, String, Void> loginAction;
    public final AsyncAction1<Boolean, Void> syncApplicationDataAction;

    /**
     * Constructor.
     */
    @Inject
    public SigninActions(AsyncActionStore asyncActionStore,
                         AuthRepository authRepository,
                         UserRepository userRepository,
                         HoustonClient houstonClient,
                         CurrencyActions currencyActions,
                         ContactActions contactActions,
                         OperationActions operationActions,
                         AddressActions addressActions,
                         UserActions userActions,
                         KeysRepository keysRepository,
                         HardwareWalletActions hardwareWalletActions,
                         FetchNextTransactionSizeAction fetchNextTransactionSize,
                         FetchRealTimeDataAction fetchRealTimeData,
                         ExecutionTransformerFactory transformerFactory) {

        this.authRepository = authRepository;
        this.houstonClient = houstonClient;
        this.userRepository = userRepository;
        this.currencyActions = currencyActions;
        this.contactActions = contactActions;
        this.operationActions = operationActions;
        this.addressActions = addressActions;
        this.userActions = userActions;
        this.keysRepository = keysRepository;

        this.createSessionAction = asyncActionStore.get("session/create", this::createSession);

        this.updateChallengeSetupAction =
                asyncActionStore.get("challenge/update", this::setupChallenge);

        this.signupAction = asyncActionStore.get("user/signup", this::signup);
        this.loginAction = asyncActionStore.get("user/login", this::login);

        this.syncApplicationDataAction =
                asyncActionStore.get("application/sync", this::syncApplicationData);

        this.hardwareWalletActions = hardwareWalletActions;
        this.fetchNextTransactionSize = fetchNextTransactionSize;
        this.fetchRealTimeData = fetchRealTimeData;
        this.transformerFactory = transformerFactory;
    }

    /**
     * Creates a new session in Houston, associated with a given email.
     */
    public Observable<CreateSessionOk> createSession(@NotNull String email) {
        return waitForFcmToken()
                .flatMap((token) -> houstonClient.createSession(
                        email,
                        Globals.INSTANCE.getOldBuildType(),
                        Globals.INSTANCE.getVersionCode(),
                        token
                )).doOnNext(ignored -> Logger.configureForUser("NotLoggedYet", email));
    }

    /**
     * Return the current FCM token, waiting a few seconds if it's not immediately available.
     */
    private Observable<String> waitForFcmToken() {
        return userRepository.watchFcmToken()
                .observeOn(transformerFactory.getBackgroundScheduler())
                .filter(token -> token != null)
                .first()
                .timeout(15, TimeUnit.SECONDS)
                .compose(ObservableFn.replaceTypedError(
                        TimeoutException.class,
                        error -> new FcmTokenNotAvailableError()
                ))
                .doOnError(Logger::error); // force-log this UserFacingError
    }

    /**
     * Fetches the user information from Houston and creates an User and stores it.
     */
    public Observable<User> fetchUserInfo() {

        return houstonClient.fetchUser()
                .doOnNext(userRepository::store)
                .doOnNext(ignored -> setupCrashlytics());
    }

    /**
     * Setups Crashlytics identifiers.
     */
    public void setupCrashlytics() {
        final User user = userRepository.fetchOne();
        Logger.configureForUser(user.hid.toString(), user.email);
    }

    /**
     * Sets the primary currency for this user.
     */
    private CurrencyUnit getPrimaryCurrency() {

        // TODO: ask the user to choose if there're multiple currencies available

        final Set<CurrencyUnit> localCurrencies = currencyActions.getLocalCurrencies();

        // TODO: This set may be empty.

        return localCurrencies.iterator().next();
    }

    /**
     * Signs up a user.
     */
    public Observable<Void> signup(SignupDraft draft) {

        final byte[] salt = userActions.generateSaltForChallengeKey();
        final ChallengePrivateKey passwordPrivateKey = ChallengePrivateKey
                .fromUserInput(draft.password, salt);
        final ChallengePublicKey passwordPublicKey = passwordPrivateKey.getChallengePublicKey();

        return Observable.defer(() -> addressActions.createAndStoreRootPrivateKey(draft.password)
                .flatMap(encryptedPrivateKey -> {
                    final CurrencyUnit primaryCurrency = getPrimaryCurrency();

                    return houstonClient.signup(
                            encryptedPrivateKey,
                            primaryCurrency,
                            keysRepository.getBasePublicKey(),
                            passwordPublicKey,
                            salt
                    );
                }))
                .doOnNext(keysRepository::storeBaseMuunPublicKey)
                .doOnNext((ignored) ->
                        userActions.storeChallengeKey(ChallengeType.PASSWORD, passwordPublicKey)
                )
                .map(RxHelper::toVoid);
    }

    /**
     * Login with a challenge.
     */
    public Observable<Void> login(ChallengeType challengeType, String userInput) {
        return houstonClient.requestChallenge(challengeType)
                .flatMap(maybeChallenge -> {
                    if (maybeChallenge.isPresent()) {
                        return loginWithChallenge(maybeChallenge.get(), userInput);

                    } else {
                        return loginCompatWithoutChallenge(userInput);
                    }
                });
    }

    public Optional<SessionStatus> getSessionStatus() {
        return authRepository.getSessionStatus();
    }

    /**
     * Returns true if the users is currently signed in.
     */
    public boolean isSignedIn() {
        return authRepository.getSessionStatus()
                .map(SessionStatus.LOGGED_IN::equals)
                .map(isLoggedIn -> isLoggedIn && !userRepository.hasSignupDraft())
                .orElse(false);
    }

    /**
     * Synchronize Apollo with Houston.
     */
    public Observable<Void> syncApplicationData(boolean haveContactsPermission) {

        final Observable<Void> syncContacts;

        if (haveContactsPermission) {
            // Sync phone contacts sending PATCH, then fetch full list:
            syncContacts = contactActions.syncPhoneContacts()
                    .flatMap(ignored -> contactActions.fetchReplaceContacts());

        } else {
            // Just fetch previous contacts, we can't PATCH with local changes:
            syncContacts = contactActions.fetchReplaceContacts();
        }

        final Observable<?> step1 = Observable.zip(
                fetchUserInfo(),
                fetchNextTransactionSize.action(),
                hardwareWalletActions.fetchReplaceHardwareWallets(),
                fetchRealTimeData.action(),
                syncContacts,
                RxHelper::toVoid
        );

        final Observable<?> step2 = Observable.zip(
                operationActions.fetchReplaceOperations(),
                addressActions.syncPublicKeySet(),
                RxHelper::toVoid
        );

        return Observable.concat(step1, step2)
                .lastOrDefault(null)
                .onErrorResumeNext(throwable -> Observable.error(new InitialSyncError(throwable)))
                .map(RxHelper::toVoid);
    }

    public void authorizeSignin() {
        authRepository.storeSessionStatus(SessionStatus.AUTHORIZED_BY_EMAIL);
    }

    /**
     * Watch for the user to confirm session.
     */
    public Completable awaitAuthorizedSignin() {
        return authRepository.awaitForAuthorizedSignin();
    }

    private Observable<Void> loginWithChallenge(Challenge challenge, String userInput) {
        final ChallengeSignature challengeSignature = signChallenge(challenge, userInput);

        return houstonClient.login(challengeSignature)
                .flatMap(keySet -> decryptStoreKeySet(keySet, userInput));
    }


    private Observable<Void> loginCompatWithoutChallenge(String password) {
        return houstonClient.loginCompatWithoutChallenge()
                .flatMap(keySet ->
                        decryptStoreKeySet(keySet, password)
                )
                .compose(ObservableFn.replaceTypedError(
                        // Without challenges, a decryption error is not necessarily an integrity
                        // error. Much more likely, the user entered the wrong password.
                        // We'll fake a wrong challenge signature.
                        PasswordIntegrityError.class,
                        error -> new InvalidChallengeSignatureError()
                ))
                .flatMap(ignored ->
                        setupChallenge(ChallengeType.PASSWORD, password)
                )
                .map(RxHelper::toVoid);
    }

    private Observable<Void> decryptStoreKeySet(KeySet keySet, String userInput) {

        if (keySet.challengePublicKeys != null) {
            for (Map.Entry<String, byte[]> challenge : keySet.challengePublicKeys.entrySet()) {
                final ChallengeType type = ChallengeType.valueOf(challenge.getKey());

                final ChallengePublicKey publicKey = ChallengePublicKey
                        .fromBytes(challenge.getValue());

                userActions.storeChallengeKey(type, publicKey);
            }
        }

        if (keySet.muunKey != null) {
            keysRepository.storeEncryptedMuunPrivateKey(keySet.muunKey);
        }

        return addressActions.decryptAndStoreRootPrivateKey(
                keySet.encryptedPrivateKey,
                userInput
        );
    }

    private ChallengeSignature signChallenge(Challenge challenge, String userInput) {
        final byte[] signatureBytes = ChallengePrivateKey
                .fromUserInput(userInput, challenge.salt)
                .sign(challenge.challenge);

        return new ChallengeSignature(challenge.type, signatureBytes);
    }

    @VisibleForTesting
    Observable<SetupChallengeResponse> setupChallenge(ChallengeType challengeType,
                                                      String userInput) {

        return userActions.buildChallengeSetup(challengeType, userInput)
                .flatMap(houstonClient::setupChallenge, Pair::new)
                .doOnNext(pair -> userActions.storeChallengeKey(
                        challengeType,
                        pair.first.publicKey
                ))
                .doOnNext(pair -> {
                    if (pair.second.muunKey != null) {
                        keysRepository.storeEncryptedMuunPrivateKey(pair.second.muunKey);
                    }
                })
                .map(pair -> pair.second);
    }
}
