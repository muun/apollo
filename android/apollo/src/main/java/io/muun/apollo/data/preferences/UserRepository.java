package io.muun.apollo.data.preferences;

import io.muun.apollo.data.preferences.adapter.JsonPreferenceAdapter;
import io.muun.apollo.data.preferences.rx.Preference;
import io.muun.apollo.data.serialization.SerializationUtils;
import io.muun.apollo.domain.model.ContactsPermissionState;
import io.muun.apollo.domain.model.CurrencyDisplayMode;
import io.muun.apollo.domain.model.User;
import io.muun.apollo.domain.model.UserPhoneNumber;
import io.muun.apollo.domain.model.UserProfile;
import io.muun.common.Optional;
import io.muun.common.model.PhoneNumber;
import io.muun.common.utils.Preconditions;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import androidx.annotation.NonNull;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import rx.Observable;

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

    private static final String INITIAL_SYNC_COMPLETED_KEY = "initial_sync_completed_key";

    private static final String RC_SETUP_IN_PROCESS = "rc_setup_in_process";

    private static final String DISPLAY_SATS = "use_sats_as_currency";

    private static final String PENDING_EMAIL_LINK = "pending_email_link";

    private static final String EMAIL_SETUP_SKIPPED_KEY = "email_setup_skipped_key";

    private static final String BALANCE_HIDDEN_KEY = "balance_hidden_key";

    private final Preference<String> lastCopiedAddress;

    private final Preference<String> pendingProfilePictureUriPreference;

    private final Preference<String> passwordChangeAuthorizedUuidPreference;

    private final Preference<ContactsPermissionState> conctactsPermissionStatePreference;

    private final Preference<Boolean> initialSyncCompletedPreference;

    private final Preference<Boolean> recoveryCodeSetupInProcessPreference;

    private final Preference<CurrencyDisplayMode> displaySatsPreference;

    private final Preference<StoredUserJson> userPreference;

    private final Preference<String> pendingEmailLinkPreference;

    // Only meaningful until 1st RecoveryMethod is setup. Afterward, user tracks its RecoveryMethods
    private final Preference<Boolean> emailSetupSkippedPreference;

    private final Preference<Boolean> balanceHiddenPreference;

    /**
     * Creates a user preference repository.
     */
    @Inject
    public UserRepository(Context context, RepositoryRegistry repositoryRegistry) {
        super(context, repositoryRegistry);

        userPreference = rxSharedPreferences
                .getObject(KEY_USER, new UserPreferenceDebugAdapter());

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

        initialSyncCompletedPreference = rxSharedPreferences.getBoolean(
                INITIAL_SYNC_COMPLETED_KEY,
                false
        );

        recoveryCodeSetupInProcessPreference = rxSharedPreferences.getBoolean(RC_SETUP_IN_PROCESS);

        displaySatsPreference = rxSharedPreferences.getEnum(
                DISPLAY_SATS,
                CurrencyDisplayMode.BTC,
                CurrencyDisplayMode.class
        );

        // Non-nullable default to assure non-nullability of values
        pendingEmailLinkPreference = rxSharedPreferences.getString(PENDING_EMAIL_LINK, "default");

        emailSetupSkippedPreference = rxSharedPreferences.getBoolean(
                EMAIL_SETUP_SKIPPED_KEY,
                false
        );

        balanceHiddenPreference = rxSharedPreferences.getBoolean(
                BALANCE_HIDDEN_KEY,
                false
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

        if (user.hasPassword) {
            emailSetupSkippedPreference.set(false);
        }
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
        final long hid = sharedPreferences.getLong("hid", -1L);

        if (hid == -1L) {
            return; // If no logged in user, then avoid setting any preferences.
        }

        final StoredUserJson value = new StoredUserJson(
                hid,
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
                null, // non-existent at migration time
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

    /**
     * Note: no longer necessary a bitcoin address, can be a Ln invoice.
     */
    @Nullable
    public String getLastCopiedAddress() {
        return lastCopiedAddress.get();
    }

    /**
     * Note: no longer necessary a bitcoin address, can be a Ln invoice.
     */
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

    public void storeInitialSyncCompleted() {
        initialSyncCompletedPreference.set(true);
    }

    public boolean isInitialSyncCompleted() {
        return initialSyncCompletedPreference.get();
    }

    public void setRecoveryCodeSetupInProcess(boolean isInProcess) {
        recoveryCodeSetupInProcessPreference.set(isInProcess);
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

    public void setPendingEmailLink(String emailLink) {
        pendingEmailLinkPreference.set(emailLink);
    }

    public String getPendingEmailLink() {
        return pendingEmailLinkPreference.get();
    }

    public void setEmailSetupSkipped() {
        emailSetupSkippedPreference.set(true);
    }

    public boolean getEmailSetupSkipped() {
        return Preconditions.checkNotNull(emailSetupSkippedPreference.get());
    }

    public void setBalanceHidden(boolean hidden) {
        balanceHiddenPreference.set(hidden);
    }

    public Observable<Boolean> watchBalanceHidden() {
        return balanceHiddenPreference.asObservable();
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class StoredUserJson {
        // WAIT
        // WARNING
        // CAREFUL
        // READ THIS, I MEAN IT:

        // We forgot to exclude this class from Proguard rules. This means that the order of
        // declaration of this attributes is important -- until we remove this class from proguard
        // and migrate the preference to a non-minified JSON this class is APPEND-ONLY.

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

        public String emergencyKitLastExportedAt;

        static StoredUserJson fromUser(User user) {
            return new StoredUserJson(
                    user.hid,
                    user.email.orElse(null),
                    user.createdAt.map(SerializationUtils::serializeDate).orElse(null),
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
                    user.emergencyKitLastExportedAt
                            .map(SerializationUtils::serializeDate)
                            .orElse(null),
                    SerializationUtils.serializeCurrencyUnit(user.unsafeGetPrimaryCurrency())
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
                              String emergencyKitLastExportedAt,
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
            this.emergencyKitLastExportedAt = emergencyKitLastExportedAt;
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

                    Optional.ofNullable(emergencyKitLastExportedAt)
                            .map(SerializationUtils::deserializeDate),

                    Optional.ofNullable(createdAt).map(SerializationUtils::deserializeDate)
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

    private static class UserPreferenceDebugAdapter extends JsonPreferenceAdapter<StoredUserJson> {

        public UserPreferenceDebugAdapter() {
            super(StoredUserJson.class);
        }

        @Override
        public StoredUserJson get(@NonNull String key, @NonNull SharedPreferences preferences) {
            return super.get(key, preferences);
        }

        @Override
        public void set(@NonNull String key,
                        @NonNull StoredUserJson value,
                        @NonNull SharedPreferences.Editor editor) {
            super.set(key, value, editor);
        }
    }
}
