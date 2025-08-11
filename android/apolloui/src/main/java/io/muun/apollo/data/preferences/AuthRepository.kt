package io.muun.apollo.data.preferences

import android.content.Context
import io.muun.apollo.data.os.secure_storage.SecureStorageProvider
import io.muun.apollo.data.preferences.rx.Preference
import io.muun.common.Optional
import io.muun.common.model.SessionStatus
import io.muun.common.utils.Encodings
import rx.Observable
import javax.inject.Inject

class AuthRepository @Inject constructor(
    context: Context,
    repositoryRegistry: RepositoryRegistry,
    private val secureStorageProvider: SecureStorageProvider,
) : BaseRepository(context, repositoryRegistry) {

    companion object {
        private const val KEY_SERVER_JWT = "server_jwt"
        private const val KEY_SESSION_STATUS = "session_status"
    }

    private val sessionStatusPreference: Preference<Optional<SessionStatus>>
        get() = rxSharedPreferences.getOptionalEnum(
            KEY_SESSION_STATUS,
            SessionStatus::class.java
        )

    override val fileName get() = "auth"

    /**
     * Returns the server jwt.
     */
    val serverJwt: Optional<String>
        get() {
            if (!secureStorageProvider.has(KEY_SERVER_JWT)) {
                return Optional.empty()
            }
            val serverJwt = secureStorageProvider[KEY_SERVER_JWT]
            return Optional.ofNullable(Encodings.bytesToString(serverJwt))
        }

    /**
     * Store server JWT on secure storage.
     */
    fun storeServerJwt(serverJwt: String) {
        secureStorageProvider.put(KEY_SERVER_JWT, Encodings.stringToBytes(serverJwt))
    }

    /**
     * Store the current session status on shared preferences.
     */
    fun storeSessionStatus(sessionStatus: SessionStatus) {
        sessionStatusPreference.set(Optional.ofNullable(sessionStatus))
    }

    /**
     * Delete session/auth related data currently stored, both in preferences as in secure storage.
     * Note: this is a special casing for signup/in flow. For normal clear (e.g logout/delete
     * wallet) BaseRepository#clear should be used (secureStorage gets wiped altogether
     * separately).
     * TODO we should probably extract server_jwt to a separate class (like we do in PinManager).
     */
    fun clearSession() {
        super.clear()
        secureStorageProvider.delete(KEY_SERVER_JWT)
    }

    /**
     * Returns the session status.
     */
    val sessionStatus: Optional<SessionStatus>
        get() = watchSessionStatus().toBlocking().first()

    /**
     * Returns an observable to watch changes of the session status.
     */
    fun watchSessionStatus(): Observable<Optional<SessionStatus>> {
        return sessionStatusPreference.asObservable()
            .map { value -> value ?: Optional.empty() }
    }

    /**
     * One-time method utility for preference migration.
     */
    fun moveJwtToSecureStorage() {
        val serverJwtPrefs = rxSharedPreferences.getString(KEY_SERVER_JWT)
        if (serverJwtPrefs.isSet) {
            val serverJwt = serverJwtPrefs.get()!!
            storeServerJwt(serverJwt)
            serverJwtPrefs.delete()
        }
    }
}