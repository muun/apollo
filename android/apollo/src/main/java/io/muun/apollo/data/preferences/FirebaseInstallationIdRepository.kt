package io.muun.apollo.data.preferences

import android.content.Context
import io.muun.apollo.data.preferences.rx.Preference
import rx.Observable
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseInstallationIdRepository @Inject constructor(
    context: Context,
    repositoryRegistry: RepositoryRegistry,
) : BaseRepository(context, repositoryRegistry) {

    companion object {
        private const val FCM_TOKEN_KEY = "fcm_token_key"
        private const val BIG_QUERY_PSEUDO_ID = "big_query_pseudo_id"
    }

    private val fcmTokenPreference: Preference<String>
        get() = rxSharedPreferences.getString(FCM_TOKEN_KEY)

    private val bigQueryPseudoIdPreference: Preference<String>
        get() = rxSharedPreferences.getString(BIG_QUERY_PSEUDO_ID)

    // legacy name to avoid migration
    override val fileName get() = "fcmToken"

    fun storeBigQueryPseudoId(id: String) {
        bigQueryPseudoIdPreference.set(id)
    }

    fun getBigQueryPseudoId() = bigQueryPseudoIdPreference.get()

    fun storeFcmToken(token: String?) {
        Timber.d("FCM: Updating token in FcmToken repository")
        fcmTokenPreference.set(token)
    }

    fun watchFcmToken(): Observable<String?> {
        return fcmTokenPreference.asObservable()
    }

    /**
     * Used solely for error reporting. We should always prioritize using watchFcmToken().
     */
    fun getFcmToken(): String? {
        return fcmTokenPreference.get()
    }
}