package io.muun.apollo.domain.action;

import io.muun.apollo.data.net.HoustonClient;
import io.muun.apollo.data.preferences.AuthRepository;
import io.muun.apollo.data.preferences.UserRepository;
import io.muun.apollo.domain.action.base.AsyncAction1;
import io.muun.apollo.domain.action.base.AsyncAction2;
import io.muun.apollo.domain.action.base.AsyncActionStore;
import io.muun.apollo.domain.action.user.UpdateProfilePictureAction;
import io.muun.apollo.domain.model.ContactsPermissionState;
import io.muun.apollo.domain.model.FeedbackCategory;
import io.muun.apollo.domain.model.User;
import io.muun.apollo.domain.model.UserPhoneNumber;
import io.muun.apollo.domain.model.UserProfile;
import io.muun.common.model.PhoneNumber;
import io.muun.common.model.SessionStatus;
import io.muun.common.model.VerificationType;

import android.net.Uri;
import androidx.annotation.Nullable;
import rx.Observable;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.money.CurrencyUnit;

@Singleton
public class UserActions {

    public static final String NOTIFY_LOGOUT_ACTION = "user/logout";

    private final UserRepository userRepository;
    private final AuthRepository authRepository;

    private final HoustonClient houstonClient;

    private final ContactActions contactActions;

    private final UpdateProfilePictureAction updateProfilePictureAction;

    public final AsyncAction1<PhoneNumber, UserPhoneNumber> createPhoneAction;
    public final AsyncAction1<VerificationType, Void> resendVerificationCodeAction;
    public final AsyncAction1<String, UserPhoneNumber> confirmPhoneAction;
    public final AsyncAction1<UserProfile, UserProfile> createProfileAction;

    public final AsyncAction2<String, String, User> updateUsernameAction;
    public final AsyncAction1<CurrencyUnit, User> updatePrimaryCurrencyAction;

    public final AsyncAction2<FeedbackCategory, String, Void> submitFeedbackAction;

    public final AsyncAction1<String, Void> notifyLogoutAction;

    /**
     * Constructor.
     */
    @Inject
    public UserActions(AsyncActionStore asyncActionStore,
                       UserRepository userRepository,
                       AuthRepository authRepository,
                       HoustonClient houstonClient,
                       ContactActions contactActions,
                       UpdateProfilePictureAction updateProfilePictureAction) {

        this.userRepository = userRepository;
        this.authRepository = authRepository;
        this.houstonClient = houstonClient;

        this.contactActions = contactActions;

        this.updateProfilePictureAction = updateProfilePictureAction;

        this.createPhoneAction = asyncActionStore
            .get("user/createPhone", this::createPhone);

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

    public Observable<String> awaitPasswordChangeEmailAuthorization() {
        return userRepository.awaitForAuthorizedPasswordChange();
    }

    public void authorizePasswordChange(String uuid) {
        userRepository.storePasswordChangeStatus(uuid);
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
