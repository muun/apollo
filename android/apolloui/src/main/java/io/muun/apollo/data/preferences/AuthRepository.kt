package io.muun.apollo.data.preferences

import android.content.Context
import io.muun.apollo.data.preferences.rx.Preference
import io.muun.apollo.data.secure_key_value_store.SecureKeyValueStoreRepository
import io.muun.apollo.domain.errors.SecureKeyValueStoreError
import io.muun.common.Optional
import io.muun.common.model.SessionStatus
import io.muun.common.utils.Encodings
import rx.Observable
import javax.inject.Inject

class AuthRepository @Inject constructor(
    context: Context,
    repositoryRegistry: RepositoryRegistry,
    private val secureKeyValueStoreRepository: SecureKeyValueStoreRepository,
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
            var serverJwt: String? = null

            try {
                secureKeyValueStoreRepository.get(KEY_SERVER_JWT).withSecret { jwtBytes ->
                    // TODO: this builds a String the JVM can't clear, so the session token
                    // outlives the wipe. Bytes won't fix it here: OkHttp only takes String
                    // headers and the token arrives as a String. The way out is libwallet
                    // owning the token. Don't copy this shape to a key or a recovery code.
                    serverJwt = Encodings.bytesToString(jwtBytes)
                }
            } catch (e: SecureKeyValueStoreError.NotFound) {
                return Optional.empty()
            }

            return Optional.ofNullable(serverJwt)
        }

    /**
     * Store server JWT on secure storage.
     */
    fun storeServerJwt(serverJwt: String) {
        val jwtBytes = Encodings.stringToBytes(serverJwt)

        try {
            secureKeyValueStoreRepository.put(KEY_SERVER_JWT, jwtBytes)
        } finally {
            // Only this copy of the session token is wiped: serverJwt is a String the JVM
            // can't clear.
            jwtBytes.fill(0)
        }
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
        secureKeyValueStoreRepository.delete(KEY_SERVER_JWT)
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