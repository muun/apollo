package io.muun.apollo.domain.action;

import io.muun.apollo.data.net.HoustonClient;
import io.muun.apollo.data.preferences.AuthRepository;
import io.muun.apollo.data.preferences.KeysRepository;
import io.muun.apollo.data.preferences.UserRepository;
import io.muun.apollo.domain.action.base.AsyncAction1;
import io.muun.apollo.domain.action.base.AsyncAction2;
import io.muun.apollo.domain.action.base.AsyncActionStore;
import io.muun.apollo.domain.action.keys.CreateChallengeSetupAction;
import io.muun.apollo.domain.action.keys.StoreChallengeKeyAction;
import io.muun.apollo.domain.action.user.UpdateProfilePictureAction;
import io.muun.apollo.domain.model.ContactsPermissionState;
import io.muun.apollo.domain.model.FeedbackCategory;
import io.muun.apollo.domain.model.PendingChallengeUpdate;
import io.muun.apollo.domain.model.User;
import io.muun.apollo.domain.model.UserPhoneNumber;
import io.muun.apollo.domain.model.UserProfile;
import io.muun.common.api.SetupChallengeResponse;
import io.muun.common.crypto.ChallengePrivateKey;
import io.muun.common.crypto.ChallengeType;
import io.muun.common.model.PhoneNumber;
import io.muun.common.model.SessionStatus;
import io.muun.common.model.VerificationType;
import io.muun.common.model.challenge.Challenge;
import io.muun.common.model.challenge.ChallengeSetup;
import io.muun.common.model.challenge.ChallengeSignature;
import io.muun.common.rx.RxHelper;
import io.muun.common.utils.RandomGenerator;

import android.net.Uri;
import androidx.annotation.Nullable;
import androidx.core.util.Pair;
import rx.Observable;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.money.CurrencyUnit;

@Singleton
public class UserActions {

    public static final String NOTIFY_LOGOUT_ACTION = "user/logout";

    private final UserRepository userRepository;
    private final KeysRepository keysRepository;
    private final AuthRepository authRepository;

    private final HoustonClient houstonClient;

    private final ContactActions contactActions;

    private final UpdateProfilePictureAction updateProfilePictureAction;
    private final CreateChallengeSetupAction createChallengeSetup;
    private final StoreChallengeKeyAction storeChallengeKey;

    public final AsyncAction1<PhoneNumber, UserPhoneNumber> createPhoneAction;
    public final AsyncAction1<VerificationType, Void> resendVerificationCodeAction;
    public final AsyncAction1<String, UserPhoneNumber> confirmPhoneAction;
    public final AsyncAction1<UserProfile, UserProfile> createProfileAction;

    public final AsyncAction2<String, String, User> updateUsernameAction;
    public final AsyncAction1<CurrencyUnit, User> updatePrimaryCurrencyAction;

    public final AsyncAction2<String, ChallengeType, PendingChallengeUpdate>
            beginPasswordChangeAction;
    public final AsyncAction2<String, String, Void> finishPasswordChangeAction;

    public final AsyncAction2<FeedbackCategory, String, Void> submitFeedbackAction;

    public final AsyncAction1<String, Void> notifyLogoutAction;

    /**
     * Constructor.
     */
    @Inject
    public UserActions(AsyncActionStore asyncActionStore,
                       UserRepository userRepository,
                       KeysRepository keysRepository,
                       AuthRepository authRepository,
                       HoustonClient houstonClient,
                       ContactActions contactActions,
                       UpdateProfilePictureAction updateProfilePictureAction,
                       CreateChallengeSetupAction createChallengeSetup,
                       StoreChallengeKeyAction storeChallengeKey) {

        this.userRepository = userRepository;
        this.keysRepository = keysRepository;
        this.authRepository = authRepository;
        this.houstonClient = houstonClient;

        this.contactActions = contactActions;

        this.updateProfilePictureAction = updateProfilePictureAction;

        this.createPhoneAction = asyncActionStore
            .get("user/createPhone", this::createPhone);
        this.createChallengeSetup = createChallengeSetup;
        this.storeChallengeKey = storeChallengeKey;

        this.resendVerificationCodeAction = asyncActionStore
            .get("user/resendCode", houstonClient::resendVerificationCode);

        this.confirmPhoneAction = asyncActionStore
            .get("user/confirm-phone", this::confirmPhone);

        this.createProfileAction = asyncActionStore
            .get("user/createProfile", this::createProfile);


        this.updateUsernameAction = asyncActionStore
            .get("user/editUsername", this::updateUsername);

        this.updatePrimaryCurrencyAction = asyncActionStore
            .get("user/editPrimaryCurrency", this::updatePrimaryCurrency);

        this.beginPasswordChangeAction = asyncActionStore
            .get("user/beginChangePassword", this::beginPasswordChange);

        this.finishPasswordChangeAction = asyncActionStore
            .get("user/finishChangePassword", this::finishPasswordChange);

        this.submitFeedbackAction = asyncActionStore
            .get("user/submitFeedbackAction", this::submitFeedback);

        this.notifyLogoutAction = asyncActionStore.get(NOTIFY_LOGOUT_ACTION, this::notifyLogout);
    }

    public void setPendingProfilePicture(@Nullable Uri uri) {
        userRepository.setPendingProfilePictureUri(uri);
    }

    /**
     * Updates the user status when the email gets verified.
     */
    public void verifyEmail() {
        userRepository.storeEmailVerified();
    }

    public Observable<Boolean> watchForEmailVerification() {
        return userRepository.fetch().map(user -> user.isEmailVerified);
    }

    /**
     * Creates the user phone number.
     */
    private Observable<UserPhoneNumber> createPhone(PhoneNumber phoneNumber) {
        return houstonClient.createPhone(phoneNumber)
                .doOnNext(userRepository::storePhoneNumber);
    }

    /**
     * Confirm the user phone number.
     */
    private Observable<UserPhoneNumber> confirmPhone(String verificationCode) {
        return houstonClient.confirmPhone(verificationCode)
                .doOnNext(userRepository::storePhoneNumber);
    }

    /**
     * Creates the user profile.
     */
    private Observable<UserProfile> createProfile(UserProfile userProfile) {
        return houstonClient.createProfile(userProfile)
                .doOnNext(userRepository::store)
                .flatMap(ignore -> updateProfilePictureAction.action());
    }

    private Observable<User> updateUsername(String firstName, String lastName) {
        return houstonClient.updateUsername(firstName, lastName)
                .doOnNext(userRepository::store);
    }

    private Observable<User> updatePrimaryCurrency(CurrencyUnit currencyUnit) {
        return houstonClient.updatePrimaryCurrency(currencyUnit)
                .doOnNext(userRepository::store);
    }

    public Observable<String> getEncryptedMuunPrivateKey() {
        return keysRepository.getEncryptedMuunPrivateKey();
    }

    public Observable<String> getEncryptedBasePrivateKey() {
        return keysRepository.getEncryptedBasePrivateKey();
    }

    public boolean hasRecoveryCode() {
        return userRepository.hasRecoveryCode();
    }

    /**
     * Starts password change process by requesting a challenge and sending a challenge signature,
     * signed with the current password or recovery code.
     */
    private Observable<PendingChallengeUpdate> beginPasswordChange(String userInput,
                                                                   ChallengeType challengeType) {

        return houstonClient.requestChallenge(challengeType)
            .flatMap(maybeChallenge -> {
                if (!maybeChallenge.isPresent()) {
                    // TODO ???
                }

                final byte[] signature = signChallenge(userInput, maybeChallenge.get());

                return houstonClient.beginPasswordChange(
                    new ChallengeSignature(challengeType, signature)
                );
            });
    }

    public Observable<String> awaitPasswordChangeEmailAuthorization() {
        return userRepository.awaitForAuthorizedPasswordChange();
    }

    public void authorizePasswordChange(String uuid) {
        userRepository.storePasswordChangeStatus(uuid);
    }

    /**
     * Finish a password change process by submitting a new ChallengeSetup, built with the
     * new password, and a process' identifying uuid.
     */
    private Observable<Void> finishPasswordChange(String uuid, String password) {
        return createChallengeSetup.action(ChallengeType.PASSWORD, password)
                .flatMap(setupChallenge ->
                        houstonClient.finishPasswordChange(uuid, setupChallenge),
                        Pair::new
                )
                .flatMap(pair -> {
                    final ChallengeSetup chSetup = pair.first;
                    final SetupChallengeResponse setupChallengeResponse = pair.second;

                    if (setupChallengeResponse.muunKey !=  null) {
                        keysRepository.storeEncryptedMuunPrivateKey(setupChallengeResponse.muunKey);
                    }

                    return storeChallengeKey.action(ChallengeType.PASSWORD, chSetup.publicKey);
                })
                .map(RxHelper::toVoid);
    }

    public byte[] generateSaltForChallengeKey() {
        return RandomGenerator.getBytes(8);
    }

    // This method is duplicated. (Also in SigninActions)
    private byte[] signChallenge(String userInput, Challenge challenge) {

        final ChallengePrivateKey challengePrivateKey = ChallengePrivateKey.fromUserInput(
                userInput,
                challenge.salt
        );

        return challengePrivateKey.sign(challenge.challenge);
    }

    private Observable<Void> submitFeedback(FeedbackCategory category, String feedback) {
        // A tiny little hack to avoid changing the Houston endpoint:
        final String body = "--- On " + category.name() + " feedback ---\n\n" + feedback;

        return houstonClient.submitFeedback(body);
    }

    public void resetPhoneNumber() {
        userRepository.storePhoneNumber(null);
    }

    public void reportContactsPermissionNeverAskAgain() {
        userRepository.storeContactsPermissionState(ContactsPermissionState.PERMANENTLY_DENIED);
    }

    public Observable<ContactsPermissionState> watchContactsPermissionState() {
        return userRepository.watchContactsPermissionState();
    }

    /**
     * Update value of user preference tracking Contacts permission state.
     * This receives as param the result of asking if permission is granted, that's why it is a
     * boolean: true for GRANTED, false for DENIED.
     *
     * <p>Helpful: table of values
     *
     * <p>if current_state is GRANTED           && new_value is GRANTED =>  GRANTED
     * if current_state is GRANTED              && new_value is DENIED  =>  DENIED
     * if current_state is DENIED               && new_value is GRANTED =>  GRANTED
     * if current_state is DENIED               && new_value is DENIED  =>  DENIED
     * if current_state is PERMANENTLY_DENIED   && new_value is GRANTED =>  GRANTED
     * if current_state is PERMANENTLY_DENIED   && new_value is DENIED  =>  PERMANENTLY_DENIED
     */
    public void updateContactsPermissionState(boolean granted) {

        final ContactsPermissionState prevState = userRepository.getContactsPermissionState();

        if (granted) {
            // if we detect a permission grant (via android settings) => trigger sync phone contacts
            if (isLoggedIn() && prevState != ContactsPermissionState.GRANTED) {
                contactActions.initialSyncPhoneContactsAction.run();
            }

            userRepository.storeContactsPermissionState(ContactsPermissionState.GRANTED);
            return;
        }

        if (prevState != ContactsPermissionState.PERMANENTLY_DENIED) {
            userRepository.storeContactsPermissionState(ContactsPermissionState.DENIED);
        }
    }

    private Observable<Void> notifyLogout(String jwtToken) {
        return houstonClient.notifyLogout("Bearer " + jwtToken);
    }

    /**
     * Returns true if the session status is available and LOGGED_IN and initial sync process is
     * fully completed.
     */
    public boolean isLoggedIn() {
        return authRepository.getSessionStatus()
                .map(SessionStatus.LOGGED_IN::equals)
                .map(isLoggedIn -> isLoggedIn && userRepository.isInitialSyncCompleted())
                .orElse(false);
    }
}
