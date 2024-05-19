package io.muun.apollo.data.preferences

import android.content.Context
import androidx.annotation.VisibleForTesting
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import io.muun.apollo.data.preferences.adapter.JsonPreferenceAdapter
import io.muun.apollo.data.preferences.rx.Preference
import io.muun.apollo.domain.model.user.UserPreferences
import io.muun.common.model.ReceiveFormatPreference
import rx.Observable
import javax.inject.Inject

open class UserPreferencesRepository @Inject constructor(
    context: Context,
    repositoryRegistry: RepositoryRegistry,
) : BaseRepository(context, repositoryRegistry) {

    companion object {
        private const val KEY = "USER_PREFERENCES"
    }

    private val preference: Preference<StoredUserPreferences> =
        rxSharedPreferences.getObject(
            KEY,
            StoredUserPreferences(),
            JsonPreferenceAdapter(StoredUserPreferences::class.java)
        )

    override fun getFileName(): String {
        return KEY
    }

    open fun watch(): Observable<UserPreferences> {
        return preference.asObservable()
            .map { it.toModel() }
    }

    open fun update(prefs: UserPreferences) {
        preference.set(StoredUserPreferences(prefs))
    }

    fun updateSkippedEmail(value: Boolean): UserPreferences {
        val updatedPrefs = preference.get()!!.toModel().copy(skippedEmailSetup = value)
        update(updatedPrefs)
        return updatedPrefs
    }

    /*
     When adding a new field:
      * Always set a default value, make sure it matches what houston and falcon say
      * The fields must be var to ensure Jackson can set the value

     NEVER, under no circumstance, change the names of properties since this
     is used for serialization.

     For complete instructions, see UserPreferences in common.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    @VisibleForTesting // internal should work but kotlin and project setup are preventing it
    class StoredUserPreferences {

        var strictMode: Boolean = false
        var seenNewHome: Boolean = false
        var seenLnurlFirstTime: Boolean = false
        var defaultAddressType: String = "segwit"
        var skippedEmailSetup: Boolean = false
        var receivePreference: ReceiveFormatPreference = ReceiveFormatPreference.ONCHAIN
        var allowMultiSession: Boolean = false

        // JSON constructor
        constructor()

        constructor(prefs: UserPreferences) : this() {
            strictMode = prefs.strictMode
            seenNewHome = prefs.seenNewHome
            seenLnurlFirstTime = prefs.seenLnurlFirstTime
            defaultAddressType = prefs.defaultAddressType
            skippedEmailSetup = prefs.skippedEmailSetup
            receivePreference = prefs.receivePreference
            allowMultiSession = prefs.allowMultiSession
        }

        fun toModel(): UserPreferences {
            return UserPreferences(
                strictMode,
                seenNewHome,
                seenLnurlFirstTime,
                defaultAddressType,
                skippedEmailSetup,
                receivePreference,
                allowMultiSession
            )
        }
    }
}
