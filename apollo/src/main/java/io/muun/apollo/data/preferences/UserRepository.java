package io.muun.apollo.data.preferences;

import io.muun.apollo.data.logging.Logger;
import io.muun.apollo.data.serialization.SerializationUtils;
import io.muun.apollo.domain.errors.NullCurrencyBugError;
import io.muun.apollo.domain.model.ContactsPermissionState;
import io.muun.apollo.domain.model.SignupDraft;
import io.muun.apollo.domain.model.User;
import io.muun.apollo.domain.model.UserPhoneNumber;
import io.muun.apollo.domain.model.UserProfile;
import io.muun.common.Optional;

import android.content.Context;
import android.net.Uri;
import com.f2prateek.rx.preferences.Preference;
import rx.Observable;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.money.CurrencyUnit;

public class UserRepository extends BaseRepository {

    private static final String KEY_HID = "hid";

    private static final String KEY_FIRST_NAME = "first_name";

    private static final String KEY_LAST_NAME = "last_name";

    private static final String KEY_PHONE_NUMBER = "phone_number";

    private static final String KEY_PHONE_NUMBER_VERIFIED = "phone_number_verified";

    private static final String KEY_PROFILE_PICTURE_URL = "profile_picture_url";

    private static final String KEY_LAST_COPIED_ADDRESS = "key_last_copied_address";

    private static final String PRIMARY_CURRENCY_KEY = "primary_currency_key";

    private static final String PENDING_PROFILE_PICTURE_URI_KEY = "pending_profile_picture_uri_key";

    private static final String EMAIL_VERIFIED_KEY = "email_verified_key";

    private static final String SIGNUP_DRAFT = "signup_draft";

    private static final String EMAIL_KEY = "email";

    private static final String HAS_RECOVERY_CODE_KEY = "has_recovery_code";

    private static final String HAS_P2P_ENABLED_KEY = "has_p2p_enabled";

    private static final String PASSWORD_CHANGE_AUTHORIZED_UUID = "password_change_authorized_uuid";

    private static final String CONTACTS_PERMISSION_STATE_KEY = "contacts_permission_state_key";

    private static final String FCM_TOKEN_KEY = "fcm_token_key";

    private final Preference<Long> hidPreference;

    private final Preference<String> firstNamePreference;

    private final Preference<String> lastNamePreference;

    private final Preference<String> phoneNumberPreference;

    private final Preference<Boolean> phoneNumberVerifiedPreference;

    private final Preference<String> profilePictureUrlPreference;

    private final Preference<String> lastCopiedAddress;

    private final Preference<String> primaryCurrencyPreference;

    private final Preference<String> pendingProfilePictureUriPreference;

    private final Preference<String> signupDraftPreference;

    private final Preference<String> emailPreference;

    private final Preference<Boolean> isEmailVerifiedPreference;

    private final Preference<Boolean> hasRecoveryCodePreference;

    private final Preference<Boolean> hasP2PEnabledPreference;

    private final Preference<String> passwordChangeAuthorizedUuidPreference;

    private final Preference<ContactsPermissionState> conctactsPermissionStatePreference;

    private final Preference<String> fcmTokenPreference;

    /**
     * Creates a user preference repository.
     */
    @Inject
    public UserRepository(Context context) {
        super(context);

        hidPreference = rxSharedPreferences.getLong(KEY_HID);
        firstNamePreference = rxSharedPreferences.getString(KEY_FIRST_NAME);
        lastNamePreference = rxSharedPreferences.getString(KEY_LAST_NAME);
        phoneNumberPreference = rxSharedPreferences.getString(KEY_PHONE_NUMBER);

        phoneNumberVerifiedPreference = rxSharedPreferences.getBoolean(
                KEY_PHONE_NUMBER_VERIFIED,
                false
        );

        profilePictureUrlPreference = rxSharedPreferences.getString(KEY_PROFILE_PICTURE_URL);
        primaryCurrencyPreference = rxSharedPreferences.getString(PRIMARY_CURRENCY_KEY);

        lastCopiedAddress = rxSharedPreferences.getString(KEY_LAST_COPIED_ADDRESS);

        pendingProfilePictureUriPreference = rxSharedPreferences.getString(
                PENDING_PROFILE_PICTURE_URI_KEY
        );

        signupDraftPreference = rxSharedPreferences.getString(SIGNUP_DRAFT);

        emailPreference = rxSharedPreferences.getString(EMAIL_KEY);

        isEmailVerifiedPreference = rxSharedPreferences.getBoolean(EMAIL_VERIFIED_KEY, false);

        hasRecoveryCodePreference = rxSharedPreferences.getBoolean(HAS_RECOVERY_CODE_KEY, false);
        hasP2PEnabledPreference = rxSharedPreferences.getBoolean(HAS_P2P_ENABLED_KEY, false);

        passwordChangeAuthorizedUuidPreference = rxSharedPreferences.getString(
                PASSWORD_CHANGE_AUTHORIZED_UUID
        );

        conctactsPermissionStatePreference = rxSharedPreferences.getEnum(
                CONTACTS_PERMISSION_STATE_KEY,
                ContactsPermissionState.DENIED,
                ContactsPermissionState.class
        );

        fcmTokenPreference = rxSharedPreferences.getString(FCM_TOKEN_KEY);
    }

    @Override
    protected String getFileName() {
        return "user";
    }

    /**
     * Stores the user.
     */
    public void store(User user) {
        hidPreference.set(user.hid);

        emailPreference.set(user.email);
        isEmailVerifiedPreference.set(user.isEmailVerified);

        storePhoneNumber(user.phoneNumber.orElse(null));
        storeProfile(user.profile.orElse(null));

        primaryCurrencyPreference.set(
                SerializationUtils.serializeCurrencyUnit(user.primaryCurrency)
        );

        hasRecoveryCodePreference.set(user.hasRecoveryCode);
        hasP2PEnabledPreference.set(user.hasP2PEnabled);
    }

    /**
     * Fetches the user.
     */
    public Observable<User> fetch() {
        return Observable.combineLatest(
                hidPreference.asObservable(),
                emailPreference.asObservable(),
                isEmailVerifiedPreference.asObservable(),
                fetchPhoneNumber(),
                fetchProfile(),
                fetchPrimaryCurrency(),
                hasRecoveryCodePreference.asObservable(),
                hasP2PEnabledPreference.asObservable(),
                User::new
        );
    }

    public User fetchOne() {
        return fetch().toBlocking().first();
    }

    private Observable<Optional<UserProfile>> fetchProfile() {
        // NOTE: this uses the previous storage model of Apollo to keep retro-compatibility.
        return Observable.combineLatest(
                firstNamePreference.asObservable(),
                lastNamePreference.asObservable(),
                profilePictureUrlPreference.asObservable(),

                (firstName, lastName, profilePicture) -> {
                    if (firstName == null) {
                        return Optional.empty();

                    } else {
                        return Optional.of(new UserProfile(firstName, lastName, profilePicture));
                    }
                }
        );
    }

    /**
     * Stores a user Profile.
     */
    public void storeProfile(@Nullable UserProfile profile) {
        if (profile == null) {
            firstNamePreference.delete();
            lastNamePreference.delete();
            profilePictureUrlPreference.delete();

        } else {
            firstNamePreference.set(profile.getFirstName());
            lastNamePreference.set(profile.getLastName());
            profilePictureUrlPreference.set(profile.getPictureUrl());
        }
    }

    private Observable<Optional<UserPhoneNumber>> fetchPhoneNumber() {
        // NOTE: this uses the previous storage model of Apollo to keep retro-compatibility.
        return Observable.combineLatest(
                phoneNumberPreference.asObservable(),
                phoneNumberVerifiedPreference.asObservable(),

                (phoneNumber, isVerified) -> {
                    if (phoneNumber == null) {
                        return Optional.empty();

                    } else {
                        return Optional.of(new UserPhoneNumber(phoneNumber, isVerified));
                    }
                }
        );
    }

    /**
     * Stores a user PhoneNumber.
     */
    public void storePhoneNumber(@Nullable UserPhoneNumber phoneNumber) {
        if (phoneNumber != null) {
            phoneNumberPreference.set(phoneNumber.toE164String());
            phoneNumberVerifiedPreference.set(phoneNumber.isVerified());

        } else {
            phoneNumberPreference.delete();
        }
    }

    private Observable<CurrencyUnit> fetchPrimaryCurrency() {
        return getPrimaryCurrencyCatchingBug();
    }

    public Long getUserHid() {
        return hidPreference.get();
    }

    /**
     * Returns the Uri of a profile picture that needs to be uploaded.
     */
    @Nullable
    public Uri getPendingProfilePictureUri() {
        final String uriString = pendingProfilePictureUriPreference.get();

        if (uriString == null) {
            return null;
        }

        return Uri.parse(uriString);
    }

    /**
     * Enqueues a profile picture to be uploaded in the future.
     */
    public void setPendingProfilePictureUri(@Nullable Uri uri) {

        if (uri == null) {
            pendingProfilePictureUriPreference.delete();
            return;
        }

        pendingProfilePictureUriPreference.set(uri.toString());
    }


    /**
     * Save an ongoing signup process.
     */
    public void storeSignupDraft(SignupDraft draft) {
        if (draft != null) {
            signupDraftPreference.set(draft.serialize());

        } else {
            signupDraftPreference.delete();
        }
    }

    /**
     * Forget an ongoing signup process.
     */
    public void clearSignupDraft() {
        storeSignupDraft(null);
    }

    /**
     * Recover an ongoing signup process.
     */
    public Optional<SignupDraft> fetchSignupDraft() {
        if (hasSignupDraft()) {
            try {
                return Optional.of(SignupDraft.deserialize(signupDraftPreference.get()));

            } catch (IllegalArgumentException ex) {
                // SignupDraft may have changed, and this is an old format. Discard it:
                Logger.error("Could not deserialize signupDraft: " + signupDraftPreference.get());
                signupDraftPreference.delete();
            }
        }

        return Optional.empty();
    }

    /**
     * Returns true if there's an ongoing signup process.
     */
    public boolean hasSignupDraft() {
        return signupDraftPreference.isSet();
    }

    @Nullable
    public String getLastCopiedAddress() {
        return lastCopiedAddress.get();
    }

    public void setLastCopiedAddress(String address) {
        lastCopiedAddress.set(address);
    }

    public void storeEmailVerified() {
        isEmailVerifiedPreference.set(true);
    }

    public boolean isEmailVerified() {
        return isEmailVerifiedPreference.get();
    }

    public void storeHasRecoveryCode(boolean value) {
        hasRecoveryCodePreference.set(value);
    }

    public Observable<Boolean> watchHasRecoveryCode() {
        return hasRecoveryCodePreference.asObservable();
    }

    public boolean hasRecoveryCode() {
        return hasRecoveryCodePreference.get();
    }

    /**
     * Wait for the authorized email notification.
     */
    public Observable<String> awaitForAuthorizedPasswordChange() {
        return passwordChangeAuthorizedUuidPreference.asObservable()
                .filter(uuid -> uuid != null && !uuid.isEmpty());
    }

    public void storePasswordChangeStatus(String uuid) {
        passwordChangeAuthorizedUuidPreference.set(uuid);
    }

    public void storeContactsPermissionState(ContactsPermissionState state) {
        conctactsPermissionStatePreference.set(state);
    }

    public ContactsPermissionState getContactsPermissionState() {
        return conctactsPermissionStatePreference.get();
    }

    public Observable<ContactsPermissionState> watchContactsPermissionState() {
        return conctactsPermissionStatePreference.asObservable();
    }

    private Observable<CurrencyUnit> getPrimaryCurrencyCatchingBug() {
        return primaryCurrencyPreference.asObservable()
            .map(code -> {
                if (code == null) {
                    Logger.error(new NullCurrencyBugError());
                    code = "USD";
                }

                return code;
            })
            .map(SerializationUtils::deserializeCurrencyUnit);
    }

    public void storeFcmToken(String token) {
        Logger.debug("FCM: Updating token in auth repository");
        fcmTokenPreference.set(token);
    }

    public Observable<String> watchFcmToken() {
        return fcmTokenPreference.asObservable();
    }

}
