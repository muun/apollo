package io.muun.apollo.domain.action.integrity

import android.content.Context
import com.google.android.play.core.integrity.IntegrityManagerFactory
import com.google.android.play.core.integrity.IntegrityTokenRequest
import com.google.android.play.core.integrity.IntegrityTokenResponse
import com.google.android.play.core.integrity.model.IntegrityErrorCode
import com.google.firebase.FirebaseApp
import io.muun.apollo.domain.errors.PlayIntegrityError
import io.muun.apollo.domain.model.PlayIntegrityToken
import rx.Observable
import rx.Observer
import rx.subjects.BehaviorSubject
import timber.log.Timber
import javax.inject.Inject


class GooglePlayIntegrity @Inject constructor(private val context: Context) {

    companion object {

        val retryableErrors = listOf(
            IntegrityErrorCode.TOO_MANY_REQUESTS,
            IntegrityErrorCode.GOOGLE_SERVER_UNAVAILABLE,
            IntegrityErrorCode.CLIENT_TRANSIENT_ERROR,
            IntegrityErrorCode.INTERNAL_ERROR
        )
    }

    fun fetchIntegrityToken(nonce: String): Observable<PlayIntegrityToken> {

        val subject = BehaviorSubject.create<PlayIntegrityToken>()

        val integrityManager = IntegrityManagerFactory.create(context)

        // This is Google Clouds Project Number, but returned as a string so we convert to long
        val projectNumber = FirebaseApp.getInstance().options.gcmSenderId!!
            .toLong()

        // Request the integrity token by providing a nonce.
        val integrityTokenResponse = integrityManager.requestIntegrityToken(
            IntegrityTokenRequest.builder()
                .setCloudProjectNumber(projectNumber)
                .setNonce(nonce)
                .build()
        )

        // Notice: all these listeners execute on Main Thread
        integrityTokenResponse.addOnSuccessListener { response: IntegrityTokenResponse ->
            try {
                handleIntegrityTokenSuccess(response, subject)

            } catch (e: Throwable) {
                handleErrorOnMainThread(e, subject)
            }
        }

        integrityTokenResponse.addOnFailureListener { e: Exception ->
            try {
                handleIntegrityTokenError(e, subject)

            } catch (e: Throwable) {
                handleErrorOnMainThread(e, subject)
            }
        }

        integrityTokenResponse.addOnCanceledListener {
            try {
                handleIntegrityTokenCanceled(subject)

            } catch (e: Throwable) {
                handleErrorOnMainThread(e, subject)
            }
        }

        return subject
    }

    private fun handleIntegrityTokenSuccess(
        response: IntegrityTokenResponse,
        observer: Observer<PlayIntegrityToken>,
    ) {

        val integrityToken = response.token()
        Timber.i("IntegrityTokenFetched: $integrityToken")

        observer.onNext(PlayIntegrityToken(integrityToken))
        observer.onCompleted()
    }

    private fun handleIntegrityTokenError(e: Exception, observer: Observer<PlayIntegrityToken>) {

        val playIntegrityError = buildPlayIntegrityError(e)

        Timber.i("IntegrityTokenError: ${playIntegrityError.getName()}")
        Timber.i("IntegrityTokenError: ${playIntegrityError.getText()}")

        Timber.e(playIntegrityError)

        observer.onError(playIntegrityError)
        observer.onCompleted()
    }

    private fun handleIntegrityTokenCanceled(observer: BehaviorSubject<PlayIntegrityToken>) {
        Timber.i("IntegrityTokenCancelled")
        Timber.e(PlayIntegrityError())

        observer.onNext(PlayIntegrityToken(null, "CANCELLED"))
        observer.onCompleted()
    }

    private fun handleErrorOnMainThread(e: Throwable, observer: Observer<PlayIntegrityToken>) {
        Timber.e(e)
        observer.onError(e)
        observer.onCompleted()
    }

    @Suppress("FoldInitializerAndIfToElvis")
    private fun buildPlayIntegrityError(error: Exception): PlayIntegrityError {
        val errorCode: Int? = getErrorCode(error)

        if (errorCode == null) {
            return PlayIntegrityError(error)
        }

        return PlayIntegrityError(
            errorCode,
            getErrorName(errorCode),
            getErrorText(errorCode),
            error
        )
    }

    @Suppress("FoldInitializerAndIfToElvis")
    private fun getErrorCode(error: Exception): Int? {
        val errorMessage = error.message

        if (errorMessage == null) {
            return null
        }

        // Pretty junk way of getting the error code but it works
        return try {
            errorMessage
                .replace("\n".toRegex(), "")
                .replace(":(.*)".toRegex(), "")
                .toInt()
        } catch (parseError: Throwable) {
            null
        }
    }

    private fun getErrorName(errorCode: Int): String {
        return when (errorCode) {
            IntegrityErrorCode.API_NOT_AVAILABLE -> "API_NOT_AVAILABLE"
            IntegrityErrorCode.NO_ERROR -> "NO_ERROR"
            IntegrityErrorCode.PLAY_STORE_NOT_FOUND -> "PLAY_STORE_NOT_FOUND"
            IntegrityErrorCode.NETWORK_ERROR -> "NETWORK_ERROR"
            IntegrityErrorCode.PLAY_STORE_ACCOUNT_NOT_FOUND -> "PLAY_STORE_ACCOUNT_NOT_FOUND"
            IntegrityErrorCode.APP_NOT_INSTALLED -> "APP_NOT_INSTALLED"
            IntegrityErrorCode.PLAY_SERVICES_NOT_FOUND -> "PLAY_SERVICES_NOT_FOUND"
            IntegrityErrorCode.APP_UID_MISMATCH -> "APP_UID_MISMATCH"
            IntegrityErrorCode.TOO_MANY_REQUESTS -> "TOO_MANY_REQUESTS"
            IntegrityErrorCode.CANNOT_BIND_TO_SERVICE -> "CANNOT_BIND_TO_SERVICE"
            IntegrityErrorCode.NONCE_TOO_SHORT -> "NONCE_TOO_SHORT"
            IntegrityErrorCode.NONCE_TOO_LONG -> "NONCE_TOO_LONG"
            IntegrityErrorCode.GOOGLE_SERVER_UNAVAILABLE -> "GOOGLE_SERVER_UNAVAILABLE"
            IntegrityErrorCode.NONCE_IS_NOT_BASE64 -> "NONCE_IS_NOT_BASE64"
            IntegrityErrorCode.PLAY_STORE_VERSION_OUTDATED -> "PLAY_STORE_VERSION_OUTDATED"
            IntegrityErrorCode.PLAY_SERVICES_VERSION_OUTDATED -> "PLAY_SERVICES_VERSION_OUTDATED"
            IntegrityErrorCode.CLOUD_PROJECT_NUMBER_IS_INVALID -> "CLOUD_PROJECT_NUMBER_IS_INVALID"
            IntegrityErrorCode.CLIENT_TRANSIENT_ERROR -> "CLIENT_TRANSIENT_ERROR"
            IntegrityErrorCode.INTERNAL_ERROR -> "INTERNAL_ERROR"
            else -> "UNKNOWN_ERROR"
        }
    }

    private fun getErrorText(errorCode: Int): String {
        return when (errorCode) {
            IntegrityErrorCode.API_NOT_AVAILABLE ->
                """
                Integrity API is not available.

                The Play Store version might be old, try updating it.
                """.trimIndent()
            IntegrityErrorCode.APP_NOT_INSTALLED ->
                """
                The calling app is not installed.

                This shouldn't happen. If it does please open an issue on Github.
                """.trimIndent()
            IntegrityErrorCode.APP_UID_MISMATCH ->
                """
                The calling app UID (user id) does not match the one from Package Manager.

                This shouldn't happen. If it does please open an issue on Github.
                """.trimIndent()
            IntegrityErrorCode.CANNOT_BIND_TO_SERVICE ->
                """
                Binding to the service in the Play Store has failed.

                This can be due to having an old Play Store version installed on the device.
                """.trimIndent()
            IntegrityErrorCode.GOOGLE_SERVER_UNAVAILABLE -> "Unknown internal Google server error."
            IntegrityErrorCode.INTERNAL_ERROR -> "Unknown internal error."
            IntegrityErrorCode.NETWORK_ERROR ->
                """
                No available network is found.

                Please check your connection.
                """.trimIndent()
            IntegrityErrorCode.NO_ERROR ->
                """
                No error has occurred.

                If you ever get this, congrats, I have no idea what it means.
                """.trimIndent()
            IntegrityErrorCode.NONCE_IS_NOT_BASE64 ->
                """
                Nonce is not encoded as a base64 web-safe no-wrap string.

                This shouldn't happen. If it does please open an issue on Github.
                """.trimIndent()
            IntegrityErrorCode.NONCE_TOO_LONG ->
                """
                Nonce length is too long.
                This shouldn't happen. If it does please open an issue on Github.
                """.trimIndent()
            IntegrityErrorCode.NONCE_TOO_SHORT ->
                """
                Nonce length is too short.
                This shouldn't happen. If it does please open an issue on Github.
                """.trimIndent()
            IntegrityErrorCode.PLAY_SERVICES_NOT_FOUND ->
                """
                Play Services is not available or version is too old.

                Try updating Google Play Services.
                """.trimIndent()
            IntegrityErrorCode.PLAY_STORE_ACCOUNT_NOT_FOUND ->
                """
                No Play Store account is found on device.

                Try logging into Play Store.
                """.trimIndent()
            IntegrityErrorCode.PLAY_STORE_NOT_FOUND ->
                """
                No Play Store app is found on device or not official version is installed.

                This app can't work without Play Store.
                """.trimIndent()
            IntegrityErrorCode.TOO_MANY_REQUESTS ->
                """
                The calling app is making too many requests to the API and hence is throttled.

                This shouldn't happen. If it does please open an issue on Github.
                """.trimIndent()
            else -> "Unknown Error"
        }
    }
}