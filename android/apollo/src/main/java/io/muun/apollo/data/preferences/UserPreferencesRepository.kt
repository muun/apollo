package io.muun.apollo.data.preferences

import android.content.Context
import io.muun.apollo.data.preferences.adapter.JsonPreferenceAdapter
import io.muun.apollo.data.preferences.rx.Preference
import io.muun.apollo.domain.model.user.UserPreferences
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

    /*
     When adding a new field:
      * Always set a default value, make sure it matches what houston and falcon say
      * The fields must be var to ensure Jackson can set the value

     NEVER, under no circumstance, change the names of properties since this
     is used for serialization.

     For complete instructions, see UserPreferences in common.
     */
    private class StoredUserPreferences {

        var strictMode: Boolean = false
        var seenNewHome: Boolean = false
        var seenLnurlFirstTime: Boolean = false
        var defaultAddressType: String = "segwit"
        var lightningDefaultForReceiving: Boolean = false

        // JSON constructor
        constructor()

        constructor(prefs: UserPreferences) : this() {
            strictMode = prefs.strictMode
            seenNewHome = prefs.seenNewHome
            seenLnurlFirstTime = prefs.seenLnurlFirstTime
            defaultAddressType = prefs.defaultAddressType
            lightningDefaultForReceiving = prefs.lightningDefaultForReceiving
        }

        fun toModel(): UserPreferences {
            return UserPreferences(
                strictMode,
                seenNewHome,
                seenLnurlFirstTime,
                defaultAddressType,
                lightningDefaultForReceiving
            )
        }
    }

}
