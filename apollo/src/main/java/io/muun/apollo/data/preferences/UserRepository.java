package io.muun.apollo.data.preferences;

import io.muun.apollo.data.preferences.adapter.JsonPreferenceAdapter;
import io.muun.apollo.data.preferences.rx.Preference;
import io.muun.apollo.data.serialization.SerializationUtils;
import io.muun.apollo.domain.errors.SignupDraftFormatError;
import io.muun.apollo.domain.model.ContactsPermissionState;
import io.muun.apollo.domain.model.CurrencyDisplayMode;
import io.muun.apollo.domain.model.SignupDraft;
import io.muun.apollo.domain.model.User;
import io.muun.apollo.domain.model.UserPhoneNumber;
import io.muun.apollo.domain.model.UserProfile;
import io.muun.common.Optional;
import io.muun.common.model.PhoneNumber;
import io.muun.common.utils.Preconditions;

import android.content.Context;
import android.net.Uri;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import rx.Observable;
import timber.log.Timber;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class UserRepository extends BaseRepository {

    private static final String KEY_USER = "user";

    private static final String KEY_LAST_COPIED_ADDRESS = "key_last_copied_address";

    private static final String PENDING_PROFILE_PICTURE_URI_KEY = "pending_profile_picture_uri_key";

    private static final String PASSWORD_CHANGE_AUTHORIZED_UUID = "password_change_authorized_uuid";

    private static final String CONTACTS_PERMISSION_STATE_KEY = "contacts_permission_state_key";

    private static final String FCM_TOKEN_KEY = "fcm_token_key";

    private static final String INITIAL_SYNC_COMPLETED_KEY = "initial_sync_completed_key";

    private static final String SIGNUP_DRAFT = "signup_draft";

    private static final String RC_SETUP_IN_PROCESS = "rc_setup_in_process";

    private static final String DISPLAY_SATS = "use_sats_as_currency";

    private final Preference<String> lastCopiedAddress;

    private final Preference<String> pendingProfilePictureUriPreference;

    private final Preference<String> passwordChangeAuthorizedUuidPreference;

    private final Preference<ContactsPermissionState> conctactsPermissionStatePreference;

    private final Preference<String> fcmTokenPreference;

    private final Preference<Boolean> initialSyncCompletedPreference;

    private final Preference<SignupDraft> signupDraftPreference;

    private final Preference<Boolean> recoveryCodeSetupInProcessPreference;

    private final Preference<CurrencyDisplayMode> displaySatsPreference;

    private final Preference<StoredUserJson> userPreference;

    /**
     * Creates a user preference repository.
     */
    @Inject
    public UserRepository(Context context) {
        super(context);

        userPreference = rxSharedPreferences
                .getObject(KEY_USER, new JsonPreferenceAdapter<>(StoredUserJson.class));

        lastCopiedAddress = rxSharedPreferences.getString(KEY_LAST_COPIED_ADDRESS);

        pendingProfilePictureUriPreference = rxSharedPreferences.getString(
                PENDING_PROFILE_PICTURE_URI_KEY
        );

        passwordChangeAuthorizedUuidPreference = rxSharedPreferences.getString(
                PASSWORD_CHANGE_AUTHORIZED_UUID
        );

        conctactsPermissionStatePreference = rxSharedPreferences.getEnum(
                CONTACTS_PERMISSION_STATE_KEY,
                ContactsPermissionState.DENIED,
                ContactsPermissionState.class
        );

        fcmTokenPreference = rxSharedPreferences.getString(FCM_TOKEN_KEY);

        initialSyncCompletedPreference = rxSharedPreferences.getBoolean(
                INITIAL_SYNC_COMPLETED_KEY,
                false
        );

        signupDraftPreference = rxSharedPreferences.getObject(
                SIGNUP_DRAFT,
                new JsonPreferenceAdapter<>(SignupDraft.class)
        );

        recoveryCodeSetupInProcessPreference = rxSharedPreferences.getBoolean(RC_SETUP_IN_PROCESS);

        displaySatsPreference = rxSharedPreferences.getEnum(
                DISPLAY_SATS,
                CurrencyDisplayMode.BTC,
                CurrencyDisplayMode.class
        );
    }

    @Override
    protected String getFileName() {
        return "user";
    }

    /**
     * Stores the user.
     */
    public synchronized void store(User user) {
        userPreference.set(StoredUserJson.fromUser(user));
    }

    /**
     * Fetches the user, throws NoSuchElementException if not present.
     */
    public Observable<User> fetch() {
        return fetchOptional().map(Optional::get);
    }

    /**
     * Fetches the user, throws NPE if not present.
     */
    public Observable<Optional<User>> fetchOptional() {
        return userPreference.asObservable()
                .map(storedUser -> {
                    if (storedUser != null) {
                        return Optional.of(storedUser.toUser());
                    } else {
                        return Optional.empty();
                    }
                });
    }

    public Optional<User> fetchOneOptional() {
        return fetchOptional().toBlocking().first();
    }

    /**
     * Execute the migration that ends the multi-preference hell.
     */
    public void migrateCthulhuToJsonPreference() {
        final StoredUserJson value = new StoredUserJson(
                sharedPreferences.getLong("hid", -1L),
                sharedPreferences.getString("email", null),
                sharedPreferences.getString("created_at", null),
                sharedPreferences.getString("phone_number", null),
                sharedPreferences.getBoolean("phone_number_verified", false),
                sharedPreferences.getString("first_name", null),
                sharedPreferences.getString("last_name", null),
                sharedPreferences.getString("profile_picture_url", null),
                sharedPreferences.getBoolean("email_verified_key", false),
                sharedPreferences.getBoolean("has_recovery_code", false),
                true, // all users had passwords before this feature
                sharedPreferences.getBoolean("has_p2p_enabled", false),
                false, // non-existent at migration time. This is a good default
                sharedPreferences.getString("primary_currency_key", "USD")
        );

        userPreference.set(value);
    }

    public User fetchOne() {
        return fetch().toBlocking().first();
    }

    /**
     * Stores a user Profile.
     */
    public synchronized void storeProfile(@Nullable UserProfile profile) {
        final StoredUserJson value = Preconditions.checkNotNull(userPreference.get());

        value.setProfileFrom(profile);
        userPreference.set(value);
    }

    /**
     * Stores a user PhoneNumber.
     */
    public synchronized void storePhoneNumber(@Nullable UserPhoneNumber phoneNumber) {
        final StoredUserJson value = Preconditions.checkNotNull(userPreference.get());

        value.setPhoneNumberFrom(phoneNumber);
        userPreference.set(value);
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

    @Nullable
    public String getLastCopiedAddress() {
        return lastCopiedAddress.get();
    }

    public void setLastCopiedAddress(String address) {
        lastCopiedAddress.set(address);
    }

    /**
     * Store the fact that the user has verified their email.
     */
    public void storeEmailVerified() {
        final StoredUserJson value = Preconditions.checkNotNull(userPreference.get());

        value.isEmailVerified = true;
        userPreference.set(value);
    }

    /**
     * Set whether the user has a recovery code available.
     */
    public void storeHasRecoveryCode(boolean hasRecoveryCode) {
        final StoredUserJson value = Preconditions.checkNotNull(userPreference.get());

        value.hasRecoveryCode = hasRecoveryCode;
        userPreference.set(value);

        if (hasRecoveryCode) {
            // This shouldn't be necessary, but instinct tells me to put it here:
            recoveryCodeSetupInProcessPreference.set(false);
        }
    }

    /**
     * Watch whether the user has a recovery code set up.
     */
    public Observable<Boolean> watchHasRecoveryCode() {
        return userPreference.asObservable()
                .map(Preconditions::checkNotNull)
                .map(it -> it.hasRecoveryCode);
    }

    public boolean hasRecoveryCode() {
        return watchHasRecoveryCode().toBlocking().first();
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

    public void storeFcmToken(String token) {
        Timber.d("FCM: Updating token in auth repository");
        fcmTokenPreference.set(token);
    }

    public Observable<String> watchFcmToken() {
        return fcmTokenPreference.asObservable();
    }

    public void storeInitialSyncCompleted() {
        initialSyncCompletedPreference.set(true);
    }

    public boolean isInitialSyncCompleted() {
        return initialSyncCompletedPreference.get();
    }

    /**
     * Save an ongoing signup process.
     */
    public void storeSignupDraft(SignupDraft draft) {
        if (draft != null) {
            signupDraftPreference.set(draft);

        } else {
            signupDraftPreference.delete();
        }
    }

    /**
     * Recover an ongoing signup process.
     */
    public Optional<SignupDraft> fetchSignupDraft() {
        if (hasSignupDraft()) {
            try {
                return Optional.ofNullable(signupDraftPreference.get());

            } catch (IllegalArgumentException ex) {
                // SignupDraft may have changed, and this is an old format. Discard it:
                final String rawValue = sharedPreferences.getString(SIGNUP_DRAFT, null);
                Timber.e(new SignupDraftFormatError(rawValue));

                signupDraftPreference.delete();
            }
        }

        return Optional.empty();
    }

    /**
     * Returns true if there's an ongoing signup process.
     */
    private boolean hasSignupDraft() {
        return signupDraftPreference.isSet();
    }

    public void setRecoveryCodeSetupInProcess(boolean isInProcess) {
        recoveryCodeSetupInProcessPreference.set(isInProcess);
    }

    public Observable<Boolean> watchRecoveryCodeSetupInProcess() {
        return recoveryCodeSetupInProcessPreference.asObservable();
    }

    public CurrencyDisplayMode getCurrencyDisplayMode() {
        return displaySatsPreference.get();
    }

    public Observable<CurrencyDisplayMode> watchCurrencyDisplayMode() {
        return displaySatsPreference.asObservable();
    }

    public void setCurrencyDisplayMode(CurrencyDisplayMode value) {
        displaySatsPreference.set(value);
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class StoredUserJson {
        public long hid;
        public String email;
        public String createdAt;

        public String phoneNumber;
        public boolean isPhoneNumberVerified;

        public String firstName;
        public String lastName;
        public String profilePictureUrl;

        public boolean isEmailVerified;
        public boolean hasRecoveryCode;
        public boolean hasPassword;
        public boolean hasP2PEnabled;
        public boolean hasExportedKeys;

        public String currency;

        static StoredUserJson fromUser(User user) {
            return new StoredUserJson(
                    user.hid,
                    user.email.orElse(null),
                    SerializationUtils.serializeDate(user.createdAt),
                    user.phoneNumber.map(PhoneNumber::toE164String).orElse(null),
                    user.phoneNumber.map(UserPhoneNumber::isVerified).orElse(false),
                    user.profile.map(UserProfile::getFirstName).orElse(null),
                    user.profile.map(UserProfile::getLastName).orElse(null),
                    user.profile.map(UserProfile::getPictureUrl).orElse(null),
                    user.isEmailVerified,
                    user.hasRecoveryCode,
                    user.hasPassword,
                    user.hasP2PEnabled,
                    user.hasExportedKeys,
                    SerializationUtils.serializeCurrencyUnit(user.primaryCurrency)
            );
        }

        /**
         * Json constructor.
         */
        public StoredUserJson() {
        }

        /**
         * Manual constructor.
         */
        public StoredUserJson(long hid,
                              String email,
                              String createdAt,
                              String phoneNumber,
                              boolean isPhoneNumberVerified,
                              String firstName,
                              String lastName,
                              String profilePictureUrl,
                              boolean isEmailVerified,
                              boolean hasRecoveryCode,
                              boolean hasPassword,
                              boolean hasP2PEnabled,
                              boolean hasExportedKeys,
                              String currency) {

            this.hid = hid;
            this.email = email;
            this.createdAt = createdAt;
            this.phoneNumber = phoneNumber;
            this.isPhoneNumberVerified = isPhoneNumberVerified;
            this.firstName = firstName;
            this.lastName = lastName;
            this.profilePictureUrl = profilePictureUrl;
            this.isEmailVerified = isEmailVerified;
            this.hasRecoveryCode = hasRecoveryCode;
            this.hasPassword = hasPassword;
            this.hasP2PEnabled = hasP2PEnabled;
            this.hasExportedKeys = hasExportedKeys;
            this.currency = currency;
        }

        User toUser() {
            return new User(
                    hid,
                    Optional.ofNullable(email),
                    isEmailVerified,

                    phoneNumber != null
                            ? Optional.of(new UserPhoneNumber(phoneNumber, isPhoneNumberVerified))
                            : Optional.empty(),

                    firstName != null
                            ? Optional.of(new UserProfile(firstName, lastName, profilePictureUrl))
                            : Optional.empty(),

                    SerializationUtils.deserializeCurrencyUnit(currency != null ? currency : "USD"),

                    hasRecoveryCode,
                    hasPassword,
                    hasP2PEnabled,
                    hasExportedKeys,

                    SerializationUtils.deserializeDate(createdAt)
            );
        }

        void setProfileFrom(@Nullable UserProfile newValue) {
            if (newValue != null) {
                firstName = newValue.getFirstName();
                lastName = newValue.getLastName();
                profilePictureUrl = newValue.getPictureUrl();
            } else {
                firstName = null;
                lastName = null;
                profilePictureUrl = null;
            }
        }

        void setPhoneNumberFrom(@Nullable UserPhoneNumber newValue) {
            if (newValue != null) {
                phoneNumber = newValue.toE164String();
                isPhoneNumberVerified = newValue.isVerified();
            } else {
                phoneNumber = null;
                isPhoneNumberVerified = false;
            }
        }
    }
}
