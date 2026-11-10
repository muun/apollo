package io.muun.apollo.domain

import android.content.Context
import android.content.SharedPreferences
import io.muun.apollo.data.external.Globals
import io.muun.apollo.data.preferences.adapter.JsonPreferenceAdapter
import io.muun.apollo.data.preferences.rx.RxSharedPreferences
import io.muun.apollo.data.secure_key_value_store.SecureKeyValueStoreRepository
import io.muun.apollo.data.serialization.SerializationUtils
import io.muun.apollo.domain.errors.SecureKeyValueStoreError
import io.muun.apollo.domain.errors.SignupDraftFormatError
import io.muun.apollo.domain.model.SignupDraft
import io.muun.common.utils.Encodings
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SignupDraftManager @Inject constructor(
    private val context: Context,
    private val secureKeyValueStoreRepository: SecureKeyValueStoreRepository,
) {

    companion object {
        private const val SIGNUP_DRAFT_KEY = "signup_draft_key"
    }

    /**
     * Restore a SignupDraft stored in secure storage, or create a fresh one if not able.
     */
    fun restore(): SignupDraft {
        var signupDraft = fetchSignupDraft() ?: SignupDraft()

        if (signupDraft.versionCode != Globals.INSTANCE.versionCode) {
            signupDraft = SignupDraft()
        }

        return signupDraft
    }

    /**
     * Save the current SignupDraft to secure storage.
     */
    fun save(signupDraft: SignupDraft?) {
        if (signupDraft == null) {
            secureKeyValueStoreRepository.delete(SIGNUP_DRAFT_KEY)
            return
        }

        val draftBytes = toBytes(signupDraft)

        try {
            secureKeyValueStoreRepository.put(SIGNUP_DRAFT_KEY, draftBytes)
        } finally {
            // In a login-with-RC flow the draft carries the recovery code in plaintext. Only
            // this copy is wiped: the JSON String and the draft's own rc can't be cleared.
            draftBytes.fill(0)
        }
    }

    fun clear() {
        secureKeyValueStoreRepository.delete(SIGNUP_DRAFT_KEY)
    }

    /**
     * One-time method utility for preference-to-secure-storage migration.
     */
    fun moveSignupDraftToSecureStorage() {
        val userRepositoryPrefs: SharedPreferences = context
            .getSharedPreferences("user", Context.MODE_PRIVATE)

        val preference = RxSharedPreferences.create(userRepositoryPrefs)
            .getObject("signup_draft", JsonPreferenceAdapter(SignupDraft::class.java))

        if (preference.isSet) {

            var signupDraft: SignupDraft? = null

            try {
                signupDraft = preference.get()

            } catch (ex: IllegalArgumentException) {
                // SignupDraft may have changed, and this is an old format. Discard it:
                val rawValue: String? = userRepositoryPrefs.getString("signup_draft", null)
                Timber.e(SignupDraftFormatError(rawValue))
                preference.delete()
            }

            save(signupDraft)
            userRepositoryPrefs.edit().remove("signup_draft").apply()
        }
    }

    /**
     * ONLY FOR BaseInstrumentationTest and preserving signupDraft when doing a
     * destroyWalletToStartClean.
     */
    fun fetchSignupDraft(): SignupDraft? {
        var signupDraft: SignupDraft? = null

        try {
            secureKeyValueStoreRepository.get(SIGNUP_DRAFT_KEY).withSecret { bytes ->
                signupDraft = fromBytesOrNull(bytes)
            }
        } catch (e: SecureKeyValueStoreError.NotFound) {
            return null
        }

        return signupDraft
    }

    /**
     * Wipes SignupDraft from userRepository prefs. For PreferencesMigrationManager to handle
     * legacy migrations.
     */
    fun legacyClear() {
        val userRepositoryPrefs: SharedPreferences = context
            .getSharedPreferences("user", Context.MODE_PRIVATE)
        userRepositoryPrefs.edit().remove("signup_draft").apply()
    }

    // TODO: migrate to a consume-in-place read so the draft never leaves the callback. Two
    // copies escape the wipe: the JSON String, and the SignupDraft itself, whose LoginWithRc.rc
    // is the recovery code in plaintext. SignupPresenter keeps it for the whole flow; don't add
    // other holders.
    private fun fromBytes(bytes: ByteArray): SignupDraft =
        SerializationUtils.deserializeJson(SignupDraft::class.java, Encodings.bytesToString(bytes))

    private fun fromBytesOrNull(bytes: ByteArray): SignupDraft? {
        return try {
            fromBytes(bytes)

        } catch (e: Exception) {
            // SignupDraft may have changed, and this is an old format. The stored record is
            // left alone, so every later read fails the same way until something overwrites it.
            // TODO: this sends the whole draft to the crash backend through
            // SignupDraftFormatError's metadata, and a login-with-RC draft holds the email and
            // the recovery code in plaintext. The legacy prefs migration does the same. Logging
            // the parse error instead isn't safe either: its MuunDeserializationError cause
            // carries the JSON in its metadata too.
            val rawValue: String = Encodings.bytesToString(bytes)
            Timber.e(SignupDraftFormatError(rawValue))
            null
        }
    }

    private fun toBytes(draft: SignupDraft): ByteArray =
        Encodings.stringToBytes(SerializationUtils.serializeJson(SignupDraft::class.java, draft))
}