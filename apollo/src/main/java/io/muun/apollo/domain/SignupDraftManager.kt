package io.muun.apollo.domain

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.VisibleForTesting
import io.muun.apollo.data.external.Globals
import io.muun.apollo.data.os.secure_storage.SecureStorageProvider
import io.muun.apollo.data.preferences.adapter.JsonPreferenceAdapter
import io.muun.apollo.data.preferences.rx.RxSharedPreferences
import io.muun.apollo.data.serialization.SerializationUtils
import io.muun.apollo.domain.errors.SignupDraftFormatError
import io.muun.apollo.domain.model.SignupDraft
import io.muun.common.utils.Encodings
import timber.log.Timber
import javax.inject.Inject

class SignupDraftManager @Inject constructor(
    private val context: Context,
    private val secureStorageProvider: SecureStorageProvider
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
    fun save(signupDraft: SignupDraft?) = if (signupDraft != null) {
        secureStorageProvider.put(SIGNUP_DRAFT_KEY, toBytes(signupDraft))
    } else {
        secureStorageProvider.delete(SIGNUP_DRAFT_KEY)
    }

    fun clear() {
        secureStorageProvider.delete(SIGNUP_DRAFT_KEY)
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

    @VisibleForTesting // For BaseInstrumentationTest only
    fun fetchSignupDraft(): SignupDraft? {
        if (!secureStorageProvider.has(SIGNUP_DRAFT_KEY)) {
            return null
        }

        val bytes = secureStorageProvider.get(SIGNUP_DRAFT_KEY)

        return try {
            fromBytes(bytes)

        } catch (e: Exception) {
            // SignupDraft may have changed, and this is an old format. Discard it:
            val rawValue: String = Encodings.bytesToString(bytes)
            Timber.e(SignupDraftFormatError(rawValue))
            null
        }
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

    private fun fromBytes(bytes: ByteArray): SignupDraft =
        SerializationUtils.deserializeJson(SignupDraft::class.java, Encodings.bytesToString(bytes))

    private fun toBytes(draft: SignupDraft): ByteArray =
        Encodings.stringToBytes(SerializationUtils.serializeJson(SignupDraft::class.java, draft))
}