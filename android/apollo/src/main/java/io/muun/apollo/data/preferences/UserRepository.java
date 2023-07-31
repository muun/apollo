package io.muun.apollo.data.preferences;

import io.muun.apollo.data.preferences.adapter.JsonPreferenceAdapter;
import io.muun.apollo.data.preferences.rx.Preference;
import io.muun.apollo.data.preferences.stored.StoredEkVerificationCodes;
import io.muun.apollo.data.serialization.SerializationUtils;
import io.muun.apollo.domain.model.BitcoinUnit;
import io.muun.apollo.domain.model.EmergencyKitExport;
import io.muun.apollo.domain.model.PermissionState;
import io.muun.apollo.domain.model.user.EmergencyKit;
import io.muun.apollo.domain.model.user.User;
import io.muun.apollo.domain.model.user.UserPhoneNumber;
import io.muun.apollo.domain.model.user.UserProfile;
import io.muun.common.Optional;
import io.muun.common.model.PhoneNumber;
import io.muun.common.utils.Preconditions;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import androidx.annotation.NonNull;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import libwallet.Libwallet;
import rx.Observable;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;
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

    private static final String BALANCE_HIDDEN_KEY = "balance_hidden_key";

    private static final String TAPROOT_CELEBRATION_PENDING = "taproot_celebration_pending";

    private final Preference<String> lastCopiedAddress;

    private final Preference<String> pendingProfilePictureUriPreference;

    private final Preference<String> passwordChangeAuthorizedUuidPreference;

    private final Preference<PermissionState> conctactsPermissionStatePreference;

    private final Preference<Boolean> initialSyncCompletedPreference;

    private final Preference<Boolean> recoveryCodeSetupInProcessPreference;

    private final Preference<BitcoinUnit> displaySatsPreference;

    private final Preference<StoredUserJson> userPreference;

    private final Preference<String> pendingEmailLinkPreference;

    private final Preference<Boolean> balanceHiddenPreference;

    // Horrible I know, but only temporary until taproot activation date. Afterwards this goes away.
    private final Preference<Boolean> taprootCelebrationPending;

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
                PermissionState.DENIED,
                PermissionState.class
        );

        initialSyncCompletedPreference = rxSharedPreferences.getBoolean(
                INITIAL_SYNC_COMPLETED_KEY,
                false
        );

        recoveryCodeSetupInProcessPreference = rxSharedPreferences.getBoolean(RC_SETUP_IN_PROCESS);

        displaySatsPreference = rxSharedPreferences.getEnum(
                DISPLAY_SATS,
                BitcoinUnit.BTC,
                BitcoinUnit.class
        );

        // Non-nullable default to assure non-nullability of values
        pendingEmailLinkPreference = rxSharedPreferences.getString(PENDING_EMAIL_LINK, "default");

        balanceHiddenPreference = rxSharedPreferences.getBoolean(
                BALANCE_HIDDEN_KEY,
                false
        );

        taprootCelebrationPending = rxSharedPreferences.getBoolean(
                TAPROOT_CELEBRATION_PENDING,
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

    /**
     * Get the user for the current session. Returns an empty Optional if there's no user currently
     * LOGGED_IN.
     */
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
                sharedPreferences.getString("primary_currency_key", "USD"),
                null, // non-existent at migration time
                null, // non-existent at migration time
                null, // non-existent at migration time
                new StoredEkVerificationCodes(), // non-existent at migration time
                new LinkedList<>()
        );

        userPreference.set(value);
    }

    /**
     * Get the user for the current session. Throws an Exception (NoSuchElementException) if there's
     * no user currently LOGGED_IN.
     */
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

    /**
     * Authorize pending password change process.
     */
    public void authorizePasswordChange(String uuid) {
        passwordChangeAuthorizedUuidPreference.set(uuid);
    }

    /**
     * Save contacts permission state.
     */
    public void storeContactsPermissionState(PermissionState state) {
        conctactsPermissionStatePreference.set(state);
    }

    public PermissionState getContactsPermissionState() {
        return conctactsPermissionStatePreference.get();
    }

    /**
     * Get an Observable to observe changes to the contacts permission preference.
     */
    public Observable<PermissionState> watchContactsPermissionState() {
        return conctactsPermissionStatePreference.asObservable();
    }

    /**
     * Save flag to signal that user has completed initial sync, and thus is probably LOGGED_IN now.
     */
    public void storeInitialSyncCompleted() {
        initialSyncCompletedPreference.set(true);
    }

    public boolean isInitialSyncCompleted() {
        //noinspection ConstantConditions
        return initialSyncCompletedPreference.get();
    }

    /**
     * Save flag to signal that RecoveryCode setup process, though started, has not been completed.
     */
    public void setRecoveryCodeSetupInProcess(boolean isInProcess) {
        recoveryCodeSetupInProcessPreference.set(isInProcess);
    }

    /**
     * Get the bitcoin unit preference.
     */
    public BitcoinUnit getBitcoinUnit() {
        return displaySatsPreference.get();
    }

    /**
     * Get an Observable to observe changes to the bitcoin unit preference.
     */
    public Observable<BitcoinUnit> watchBitcoinUnit() {
        return displaySatsPreference.asObservable();
    }

    /**
     * Save a new bitcoin unit preference value.
     */
    public void setBitcoinUnit(BitcoinUnit value) {
        displaySatsPreference.set(value);
    }

    /**
     * Save email action link that is being await for confirmation (via email link click). This
     * is used to distinguish between the different email links actions that we have.
     */
    public void setPendingEmailLink(String emailLink) {
        pendingEmailLinkPreference.set(emailLink);
    }

    public String getPendingEmailLink() {
        return pendingEmailLinkPreference.get();
    }

    /**
     * Save user's choice to see her wallet balance hidden in our home screen.
     */
    public void setBalanceHidden(boolean hidden) {
        balanceHiddenPreference.set(hidden);
    }

    /**
     * Get an Observable to observe changes to the balance hidden preference.
     */
    public Observable<Boolean> watchBalanceHidden() {
        return balanceHiddenPreference.asObservable();
    }

    /**
     * Save whether the taproot celebration is in order.
     */
    public void setPendingTaprootCelebration(boolean isCelebrationPending) {
        taprootCelebrationPending.set(isCelebrationPending);
    }

    /**
     * Get an Observable to observe changes to the Taproot Celebration pending preference.
     */
    public Observable<Boolean> watchPendingTaprootCelebration() {
        return taprootCelebrationPending.asObservable();
    }

    /**
     * Migration to init emergency kit version.
     */
    public void initEmergencyKitVersion() {
        final StoredUserJson storedUser = userPreference.get();
        if (storedUser != null) {
            storedUser.initEmergencyKitVersion();
        }
        userPreference.set(storedUser);
    }

    /**
     * Save latest emergency kit verification code show to user. This can either be a verification
     * code that is later successfully used, or not.
     */
    public void storeEmergencyKitVerificationCode(String verificationCode) {
        final StoredUserJson value = Preconditions.checkNotNull(userPreference.get());

        value.emergencyKitCodes.addNewest(verificationCode);
        userPreference.set(value);
    }

    /**
     * Record that this user has successfully completed the password backup (e.g they have set up
     * their password Challenge Key).
     * Note: Assumes there's currently a LOGGED_IN user store in this repository.
     */
    public void setHasPassword() {
        final StoredUserJson value = Preconditions.checkNotNull(userPreference.get());

        value.hasPassword = true;
        userPreference.set(value);
    }

    /**
     * Record that this user has successfully completed the Recovery Code backup (e.g they have set
     * up their Recovery Code Challenge Key).
     * Note: Assumes there's currently a LOGGED_IN user store in this repository.
     */
    public void setHasRecoveryCode() {
        final StoredUserJson value = Preconditions.checkNotNull(userPreference.get());

        value.hasRecoveryCode = true;
        userPreference.set(value);
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
        public Integer emergencyKitVersion;
        public String emergencyKitExportMethod;

        @NonNull // Not backed by Houston, cached locally
        public StoredEkVerificationCodes emergencyKitCodes = new StoredEkVerificationCodes();

        @NonNull
        public List<Integer> ekVersions = new LinkedList<>();

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
                    SerializationUtils.serializeCurrencyUnit(user.unsafeGetPrimaryCurrency()),
                    user.emergencyKit
                            .map(ek -> ek.getLastExportedAt())
                            .map(SerializationUtils::serializeDate)
                            .orElse(null),
                    user.emergencyKit.map(ek -> ek.getVersion()).orElse(null),
                    user.emergencyKit.map(ek -> ek.getExportMethod()).orElse(null),
                    user.emergencyKitVerificationCodes,
                    new ArrayList<>(user.emergencyKitVersions)
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
                              String currency,
                              String emergencyKitLastExportedAt,
                              Integer emergencyKitVersion,
                              EmergencyKitExport.Method emergencyKitExportMethod,
                              @NonNull StoredEkVerificationCodes ekVerificationCodes,
                              @NonNull List<Integer> ekVersions) {

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
            this.emergencyKitLastExportedAt = emergencyKitLastExportedAt;
            this.emergencyKitVersion = emergencyKitVersion;
            this.emergencyKitExportMethod = emergencyKitExportMethod != null
                    ? emergencyKitExportMethod.name()
                    : null;
            this.emergencyKitCodes = ekVerificationCodes;
            this.ekVersions = ekVersions;
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

                    emergencyKitLastExportedAt != null
                            ? Optional.of(buildEK())
                            : Optional.empty(),

                    emergencyKitCodes,
                    new TreeSet<>(ekVersions),

                    Optional.ofNullable(createdAt).map(SerializationUtils::deserializeDate)
            );
        }

        void initEmergencyKitVersion() {
            if (emergencyKitLastExportedAt != null) {
                emergencyKitVersion = (int) Libwallet.EKVersionDescriptors;
                ekVersions.add((int) Libwallet.EKVersionDescriptors);
                // We can't know which method was used for prior exports so...
                emergencyKitExportMethod = null;
            }
        }

        EmergencyKit buildEK() {
            Preconditions.checkNotNull(emergencyKitLastExportedAt);
            return new EmergencyKit(
                    Objects.requireNonNull(
                            SerializationUtils.deserializeDate(emergencyKitLastExportedAt)
                    ),
                    emergencyKitVersion,
                    emergencyKitExportMethod != null
                            ? EmergencyKitExport.Method.valueOf(emergencyKitExportMethod)
                            : null
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
