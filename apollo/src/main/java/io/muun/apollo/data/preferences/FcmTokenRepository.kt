package io.muun.apollo.data.preferences

import android.content.Context
import rx.Observable
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FcmTokenRepository @Inject constructor(context: Context): BaseRepository(context) {

    companion object {
        private const val FCM_TOKEN_KEY = "fcm_token_key"
    }

    private val fcmTokenPreference = rxSharedPreferences.getString(FCM_TOKEN_KEY)

    override fun getFileName(): String? {
        return "fcmToken"
    }

    fun storeFcmToken(token: String?) {
        Timber.d("FCM: Updating token in FcmToken repository")
        fcmTokenPreference.set(token)
    }

    fun watchFcmToken(): Observable<String?> {
        return fcmTokenPreference.asObservable()
    }
}