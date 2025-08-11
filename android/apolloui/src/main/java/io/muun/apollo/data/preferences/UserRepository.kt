package io.muun.apollo.data.preferences

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import io.muun.apollo.data.preferences.UserRepository.StoredUserJson
import io.muun.apollo.data.preferences.adapter.JsonPreferenceAdapter
import io.muun.apollo.data.preferences.rx.Preference
import io.muun.apollo.data.preferences.stored.StoredEkVerificationCodes
import io.muun.apollo.data.serialization.SerializationUtils
import io.muun.apollo.domain.model.BitcoinUnit
import io.muun.apollo.domain.model.EmergencyKitExport
import io.muun.apollo.domain.model.PermissionState
import io.muun.apollo.domain.model.user.EmergencyKit
import io.muun.apollo.domain.model.user.User
import io.muun.apollo.domain.model.user.UserPhoneNumber
import io.muun.apollo.domain.model.user.UserProfile
import io.muun.common.Optional
import io.muun.common.model.Currency
import io.muun.common.utils.Preconditions
import libwallet.Libwallet
import rx.Observable
import java.util.LinkedList
import java.util.TreeSet
import javax.inject.Inject
import javax.inject.Singleton
import javax.money.CurrencyUnit

@Singleton
class UserRepository @Inject constructor(
    context: Context,
    repositoryRegistry: RepositoryRegistry,
) : BaseRepository(context, repositoryRegistry) {

    companion object {
        private const val KEY_USER = "user"
        private const val KEY_LAST_COPIED_ADDRESS = "key_last_copied_address"
        private const val PENDING_PROFILE_PICTURE_URI_KEY = "pending_profile_picture_uri_key"
        private const val PASSWORD_CHANGE_AUTHORIZED_UUID = "password_change_authorized_uuid"
        private const val CONTACTS_PERMISSION_STATE_KEY = "contacts_permission_state_key"
        private const val INITIAL_SYNC_COMPLETED_KEY = "initial_sync_completed_key"
        private const val RC_SETUP_IN_PROCESS = "rc_setup_in_process"
        private const val DISPLAY_SATS = "use_sats_as_currency"
        private const val PENDING_EMAIL_LINK = "pending_email_link"
        private const val BALANCE_HIDDEN_KEY = "balance_hidden_key"
        private const val TAPROOT_CELEBRATION_PENDING = "taproot_celebration_pending"
        private const val SEEN_WELCOME_TO_MUUN_DIALOG_KEY = "seen_welcome_to_muun_dialog_key"

    }

    // Preferences:
    private val userPreference: Preference<StoredUserJson>
        get() = rxSharedPreferences.getObject(KEY_USER, UserPreferenceDebugAdapter())
    private val lastCopiedAddress: Preference<String>
        get() = rxSharedPreferences.getString(KEY_LAST_COPIED_ADDRESS)
    private val pendingProfilePictureUriPreference: Preference<String>
        get() = rxSharedPreferences.getString(PENDING_PROFILE_PICTURE_URI_KEY)
    private val passwordChangeAuthorizedUuidPreference: Preference<String>
        get() = rxSharedPreferences.getString(PASSWORD_CHANGE_AUTHORIZED_UUID)
    private val conctactsPermissionStatePreference: Preference<PermissionState>
        get() = rxSharedPreferences.getEnum(
            CONTACTS_PERMISSION_STATE_KEY,
            PermissionState.DENIED,
            PermissionState::class.java
        )
    private val initialSyncCompletedPreference: Preference<Boolean>
        get() = rxSharedPreferences.getBoolean(INITIAL_SYNC_COMPLETED_KEY, false)
    private val recoveryCodeSetupInProcessPreference: Preference<Boolean>
        get() = rxSharedPreferences.getBoolean(RC_SETUP_IN_PROCESS)
    private val displaySatsPreference: Preference<BitcoinUnit>
        get() = rxSharedPreferences.getEnum(
            DISPLAY_SATS,
            BitcoinUnit.BTC,
            BitcoinUnit::class.java
        )
    private val pendingEmailLinkPreference: Preference<String>
        get() = rxSharedPreferences.getString(PENDING_EMAIL_LINK, "default")
    private val balanceHiddenPreference: Preference<Boolean>
        get() = rxSharedPreferences.getBoolean(BALANCE_HIDDEN_KEY, false)

    // Horrible I know, but only temporary until taproot activation date. Afterwards this goes away.
    private val taprootCelebrationPending: Preference<Boolean>
        get() = rxSharedPreferences.getBoolean(TAPROOT_CELEBRATION_PENDING, false)
    private val seenWelcomeToMuunDialogPreference: Preference<Boolean>
        get() = rxSharedPreferences.getBoolean(SEEN_WELCOME_TO_MUUN_DIALOG_KEY, false)

    override val fileName get() = "user"

    /**
     * Stores the user.
     */
    @Synchronized
    fun store(user: User) {
        userPreference.set(StoredUserJson.fromUser(user))
    }

    /**
     * Fetches the user, throws NoSuchElementException if not present.
     */
    fun fetch(): Observable<User> {
        return fetchOptional().map { obj: Optional<User> -> obj.get() }
    }

    /**
     * Fetches the user, throws NPE if not present.
     */
    fun fetchOptional(): Observable<Optional<User>> {
        return userPreference.asObservable()
            .map { storedUser: StoredUserJson? ->
                if (storedUser != null) {
                    return@map Optional.of(storedUser.toUser())
                } else {
                    return@map Optional.empty<User>()
                }
            }
    }

    /**
     * Get the user for the current session. Returns an empty Optional if there's no user currently
     * LOGGED_IN.
     */
    fun fetchOneOptional(): Optional<User> {
        return fetchOptional().toBlocking().first()
    }

    /**
     * Execute the migration that ends the multi-preference hell.
     */
    fun migrateCthulhuToJsonPreference() {
        val hid = sharedPreferences.getLong("hid", -1L)
        if (hid == -1L) {
            return  // If no logged in user, then avoid setting any preferences.
        }
        val value = StoredUserJson(
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
            true,  // all users had passwords before this feature
            sharedPreferences.getBoolean("has_p2p_enabled", false),
            false,  // non-existent at migration time. This is a good default
            sharedPreferences.getString("primary_currency_key", "USD")!!,
            null,  // non-existent at migration time
            null,  // non-existent at migration time
            null,  // non-existent at migration time
            StoredEkVerificationCodes(),  // non-existent at migration time
            LinkedList()
        )
        userPreference.set(value)
    }

    /**
     * Get the user for the current session. Throws an Exception (NoSuchElementException) if there's
     * no user currently LOGGED_IN.
     */
    fun fetchOne(): User {
        return fetch().toBlocking().first()
    }

    /**
     * Stores a user Profile.
     */
    @Synchronized
    fun storeProfile(profile: UserProfile?) {
        val value = Preconditions.checkNotNull(userPreference.get())
        value.setProfileFrom(profile)
        userPreference.set(value)
    }

    /**
     * Stores a user PhoneNumber.
     */
    @Synchronized
    fun storePhoneNumber(phoneNumber: UserPhoneNumber?) {
        val value = Preconditions.checkNotNull(userPreference.get())
        value.setPhoneNumberFrom(phoneNumber)
        userPreference.set(value)
    }

    var pendingProfilePictureUri: Uri?
        /**
         * Returns the Uri of a profile picture that needs to be uploaded.
         */
        get() {
            val uriString = pendingProfilePictureUriPreference.get()
            return uriString?.let { Uri.parse(uriString) }
        }
        /**
         * Enqueues a profile picture to be uploaded in the future.
         */
        set(uri) {
            if (uri == null) {
                pendingProfilePictureUriPreference.delete()
                return
            }
            pendingProfilePictureUriPreference.set(uri.toString())
        }

    /**
     * Note: no longer necessary a bitcoin address, can be a Ln invoice.
     */
    var lastCopiedContentFromReceive: String?
        get() = lastCopiedAddress.get()
        set(content) {
            lastCopiedAddress.set(content)
        }

    /**
     * Store the fact that the user has verified their email.
     */
    fun storeEmailVerified() {
        val value = Preconditions.checkNotNull(userPreference.get())
        value.isEmailVerified = true
        userPreference.set(value)
    }

    /**
     * Wait for the authorized email notification.
     */
    fun awaitForAuthorizedPasswordChange(): Observable<String> {
        return passwordChangeAuthorizedUuidPreference.asObservable()
            .filter { uuid: String? -> uuid != null && uuid.isNotEmpty() }
    }

    /**
     * Authorize pending password change process.
     */
    fun authorizePasswordChange(uuid: String) {
        passwordChangeAuthorizedUuidPreference.set(uuid)
    }

    /**
     * Save contacts permission state.
     */
    fun storeContactsPermissionState(state: PermissionState) {
        conctactsPermissionStatePreference.set(state)
    }

    val contactsPermissionState: PermissionState
        get() = conctactsPermissionStatePreference.get()!!

    /**
     * Get an Observable to observe changes to the contacts permission preference.
     */
    fun watchContactsPermissionState(): Observable<PermissionState> {
        return conctactsPermissionStatePreference.asObservable()
    }

    /**
     * Save flag to signal that user has completed initial sync, and thus is probably LOGGED_IN now.
     */
    fun storeInitialSyncCompleted() {
        initialSyncCompletedPreference.set(true)
    }

    val isInitialSyncCompleted: Boolean
        get() = initialSyncCompletedPreference.get()!!

    /**
     * Save flag to signal that RecoveryCode setup process, though started, has not been completed.
     */
    fun setRecoveryCodeSetupInProcess(isInProcess: Boolean) {
        recoveryCodeSetupInProcessPreference.set(isInProcess)
    }
    /**
     * Get the bitcoin unit preference.
     */
    /**
     * Save a new bitcoin unit preference value.
     */
    var bitcoinUnit: BitcoinUnit
        get() = displaySatsPreference.get()!!
        set(value) {
            displaySatsPreference.set(value)
        }

    /**
     * Get an Observable to observe changes to the bitcoin unit preference.
     */
    fun watchBitcoinUnit(): Observable<BitcoinUnit> {
        return displaySatsPreference.asObservable()
    }

    /**
     * Save email action link that is being await for confirmation (via email link click). This
     * is used to distinguish between the different email links actions that we have.
     */
    var pendingEmailLink: String
        get() = pendingEmailLinkPreference.get()!!
        set(emailLink) {
            pendingEmailLinkPreference.set(emailLink)
        }

    /**
     * Save user's choice to see her wallet balance hidden in our home screen.
     */
    fun setBalanceHidden(hidden: Boolean) {
        balanceHiddenPreference.set(hidden)
    }

    /**
     * Get an Observable to observe changes to the balance hidden preference.
     */
    fun watchBalanceHidden(): Observable<Boolean> {
        return balanceHiddenPreference.asObservable()
    }

    /**
     * Save whether the taproot celebration is in order.
     */
    fun setPendingTaprootCelebration(isCelebrationPending: Boolean) {
        taprootCelebrationPending.set(isCelebrationPending)
    }

    /**
     * Get an Observable to observe changes to the Taproot Celebration pending preference.
     */
    fun watchPendingTaprootCelebration(): Observable<Boolean> {
        return taprootCelebrationPending.asObservable()
    }

    /**
     * Save a flag signalling that the "Welcome to Muun" dialog has been shown/seen.
     */
    fun setWelcomeToMuunDialogSeen() {
        seenWelcomeToMuunDialogPreference.set(true)
    }

    /**
     * Get whether the user has seen the "Welcome to Muun" dialog or not.
     */
    val welcomeToMuunDialogSeen: Boolean
        get() = seenWelcomeToMuunDialogPreference.get()!!

    /**
     * Migration to init emergency kit version.
     */
    fun initEmergencyKitVersion() {
        val storedUser = userPreference.get()
        storedUser?.initEmergencyKitVersion()
        userPreference.set(storedUser)
    }

    /**
     * Save latest emergency kit verification code show to user. This can either be a verification
     * code that is later successfully used, or not.
     */
    fun storeEmergencyKitVerificationCode(verificationCode: String) {
        val value = Preconditions.checkNotNull(userPreference.get())
        value.emergencyKitCodes.addNewest(verificationCode)
        userPreference.set(value)
    }

    /**
     * Record that this user has successfully completed the password backup (e.g they have set up
     * their password Challenge Key).
     * Note: Assumes there's currently a LOGGED_IN user store in this repository.
     */
    fun setHasPassword() {
        val value = Preconditions.checkNotNull(userPreference.get())
        value.hasPassword = true
        userPreference.set(value)
    }

    /**
     * Record that this user has successfully completed the Recovery Code backup (e.g they have set
     * up their Recovery Code Challenge Key).
     * Note: Assumes there's currently a LOGGED_IN user store in this repository.
     */
    fun setHasRecoveryCode() {
        val value = Preconditions.checkNotNull(userPreference.get())
        value.hasRecoveryCode = true
        userPreference.set(value)
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    class StoredUserJson {
        // WAIT
        // WARNING
        // CAREFUL
        // READ THIS, I MEAN IT:
        // We forgot to exclude this class from Proguard rules. This means that the order of
        // declaration of this attributes is important -- until we remove this class from proguard
        // and migrate the preference to a non-minified JSON this class is APPEND-ONLY.
        @JvmField
        var hid: Long = 0

        @JvmField
        var email: String? = null

        @JvmField
        var createdAt: String? = null

        @JvmField
        var phoneNumber: String? = null

        @JvmField
        var isPhoneNumberVerified = false

        @JvmField
        var firstName: String? = null

        @JvmField
        var lastName: String? = null

        @JvmField
        var profilePictureUrl: String? = null

        @JvmField
        var isEmailVerified = false

        @JvmField
        var hasRecoveryCode = false

        @JvmField
        var hasPassword = false

        @JvmField
        var hasP2PEnabled = false

        @JvmField
        var hasExportedKeys = false

        @JvmField
        var currency: String? = null

        @JvmField
        var emergencyKitLastExportedAt: String? = null

        @JvmField
        var emergencyKitVersion: Int? = null

        @JvmField
        var emergencyKitExportMethod: String? = null

        // Not backed by Houston, cached locally
        @JvmField
        var emergencyKitCodes = StoredEkVerificationCodes()

        @JvmField
        var ekVersions: MutableList<Int> = LinkedList()

        /**
         * Json constructor.
         */
        @Suppress("unused")
        constructor()

        /**
         * Manual constructor.
         */
        constructor(
            hid: Long,
            email: String?,
            createdAt: String?,
            phoneNumber: String?,
            isPhoneNumberVerified: Boolean,
            firstName: String?,
            lastName: String?,
            profilePictureUrl: String?,
            isEmailVerified: Boolean,
            hasRecoveryCode: Boolean,
            hasPassword: Boolean,
            hasP2PEnabled: Boolean,
            hasExportedKeys: Boolean,
            currency: String,
            emergencyKitLastExportedAt: String?,
            emergencyKitVersion: Int?,
            emergencyKitExportMethod: EmergencyKitExport.Method?,
            ekVerificationCodes: StoredEkVerificationCodes,
            ekVersions: MutableList<Int>,
        ) {
            this.hid = hid
            this.email = email
            this.createdAt = createdAt
            this.phoneNumber = phoneNumber
            this.isPhoneNumberVerified = isPhoneNumberVerified
            this.firstName = firstName
            this.lastName = lastName
            this.profilePictureUrl = profilePictureUrl
            this.isEmailVerified = isEmailVerified
            this.hasRecoveryCode = hasRecoveryCode
            this.hasPassword = hasPassword
            this.hasP2PEnabled = hasP2PEnabled
            this.hasExportedKeys = hasExportedKeys
            this.currency = currency
            this.emergencyKitLastExportedAt = emergencyKitLastExportedAt
            this.emergencyKitVersion = emergencyKitVersion
            this.emergencyKitExportMethod = emergencyKitExportMethod?.name
            emergencyKitCodes = ekVerificationCodes
            this.ekVersions = ekVersions
        }

        fun toUser(): User {
            return User(
                hid,
                Optional.ofNullable(email),
                isEmailVerified,
                if (phoneNumber != null) {
                    Optional.of(UserPhoneNumber(phoneNumber, isPhoneNumberVerified))
                } else {
                    Optional.empty()
                },
                if (firstName != null) {
                    Optional.of(UserProfile(firstName, lastName, profilePictureUrl))
                } else {
                    Optional.empty()
                },
                loadCurrencyFromStorage(),
                hasRecoveryCode,
                hasPassword,
                hasP2PEnabled,
                hasExportedKeys,
                if (emergencyKitLastExportedAt != null) Optional.of(buildEK()) else Optional.empty(),
                emergencyKitCodes,
                TreeSet(ekVersions),
                Optional.ofNullable(createdAt).map(SerializationUtils::deserializeDate)
            )
        }

        private fun loadCurrencyFromStorage(): CurrencyUnit {
            return try {
                val currencyCode = if (currency != null) currency!! else "USD"
                SerializationUtils.deserializeCurrencyUnit(currencyCode)
            } catch (e: Exception) {
                // This can happen for example if user primary currency is no longer supported
                // after an app or OS update.
                Currency.getUnit(Currency.DEFAULT.code).get()
            }
        }

        fun initEmergencyKitVersion() {
            if (emergencyKitLastExportedAt != null) {
                emergencyKitVersion = Libwallet.EKVersionDescriptors.toInt()
                ekVersions.add(Libwallet.EKVersionDescriptors.toInt())
                // We can't know which method was used for prior exports so...
                emergencyKitExportMethod = null
            }
        }

        private fun buildEK(): EmergencyKit {
            Preconditions.checkNotNull(emergencyKitLastExportedAt)
            return EmergencyKit(
                requireNotNull(SerializationUtils.deserializeDate(emergencyKitLastExportedAt)),
                emergencyKitVersion!!,
                if (emergencyKitExportMethod != null) {
                    EmergencyKitExport.Method.valueOf(emergencyKitExportMethod!!)
                } else {
                    null
                }
            )
        }

        fun setProfileFrom(newValue: UserProfile?) {
            if (newValue != null) {
                firstName = newValue.firstName
                lastName = newValue.lastName
                profilePictureUrl = newValue.pictureUrl
            } else {
                firstName = null
                lastName = null
                profilePictureUrl = null
            }
        }

        fun setPhoneNumberFrom(newValue: UserPhoneNumber?) {
            if (newValue != null) {
                phoneNumber = newValue.toE164String()
                isPhoneNumberVerified = newValue.isVerified
            } else {
                phoneNumber = null
                isPhoneNumberVerified = false
            }
        }

        companion object {
            fun fromUser(user: User): StoredUserJson {
                return StoredUserJson(
                    user.hid,
                    user.email.orElse(null),
                    user.createdAt.map(SerializationUtils::serializeDate).orElse(null),
                    user.phoneNumber.map(UserPhoneNumber::toE164String).orElse(null),
                    user.phoneNumber.map { obj: UserPhoneNumber -> obj.isVerified }.orElse(false),
                    user.profile.map { obj: UserProfile -> obj.firstName }.orElse(null),
                    user.profile.map { obj: UserProfile -> obj.lastName }.orElse(null),
                    user.profile.map { obj: UserProfile -> obj.pictureUrl }.orElse(null),
                    user.isEmailVerified,
                    user.hasRecoveryCode,
                    user.hasPassword,
                    user.hasP2PEnabled,
                    user.hasExportedKeys,
                    SerializationUtils.serializeCurrencyUnit(user.unsafeGetPrimaryCurrency()),
                    user.emergencyKit
                        .map { ek -> ek.lastExportedAt }
                        .map(SerializationUtils::serializeDate)
                        .orElse(null),
                    user.emergencyKit.map { ek -> ek.version }.orElse(null),
                    user.emergencyKit.map { ek -> ek.exportMethod }.orElse(null),
                    user.emergencyKitVerificationCodes,
                    ArrayList(user.emergencyKitVersions)
                )
            }
        }
    }

    /**
     * Useful for adding debugging prints.
     */
    private class UserPreferenceDebugAdapter : JsonPreferenceAdapter<StoredUserJson>(
        StoredUserJson::class.java
    ) {
        override fun get(key: String, preferences: SharedPreferences): StoredUserJson {
            return super.get(key, preferences)!!
        }

        @Suppress("RedundantOverride")
        override fun set(
            key: String,
            value: StoredUserJson,
            editor: SharedPreferences.Editor,
        ) {
            super.set(key, value, editor)
        }
    }
}