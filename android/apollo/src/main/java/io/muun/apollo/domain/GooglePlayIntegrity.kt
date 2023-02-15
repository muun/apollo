package io.muun.apollo.domain

import android.content.Context
import com.google.android.play.core.integrity.IntegrityManagerFactory
import com.google.android.play.core.integrity.IntegrityTokenRequest
import com.google.android.play.core.integrity.IntegrityTokenResponse
import com.google.android.play.core.integrity.model.IntegrityErrorCode
import io.muun.apollo.data.logging.Crashlytics
import io.muun.apollo.data.os.Configuration
import io.muun.apollo.domain.model.PlayIntegrityToken
import rx.Observable
import rx.subjects.BehaviorSubject
import javax.inject.Inject


class GooglePlayIntegrity @Inject constructor(
    private val context: Context,
    private val conf: Configuration,
) {

    fun fetchIntegrityToken(nonce: String): Observable<PlayIntegrityToken> {

        val subject = BehaviorSubject.create<PlayIntegrityToken>()

        val integrityManager = IntegrityManagerFactory.create(context)

        // Request the integrity token by providing a nonce.
        val integrityTokenResponse = integrityManager.requestIntegrityToken(
            IntegrityTokenRequest.builder()
                .setCloudProjectNumber(conf.getLong("googleCloud.projectNumber"))
                .setNonce(nonce)
                .build()
        )

        integrityTokenResponse.addOnSuccessListener { response: IntegrityTokenResponse ->
            val integrityToken = response.token()
            Crashlytics.logBreadcrumb("IntegrityTokenFetched: $integrityToken")

            subject.onNext(PlayIntegrityToken(integrityToken, null))
            subject.onCompleted()
        }

        integrityTokenResponse.addOnFailureListener { e: Exception ->

            Crashlytics.logBreadcrumb("IntegrityTokenError: ${getErrorName(e)}")
            Crashlytics.logBreadcrumb("IntegrityTokenError: ${getErrorText(e)}")

            subject.onNext(PlayIntegrityToken(null, getErrorName(e)))
            subject.onCompleted()
        }

        return subject
    }

    private fun getErrorName(error: Exception): String {
        val msg = error.message ?: return "UNKNOWN_ERROR"

        return when (getErrorCode(msg)) {
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

    private fun getErrorCode(errorMessage: String): Int {
        // Pretty junk way of getting the error code but it works
        return errorMessage
            .replace("\n".toRegex(), "")
            .replace(":(.*)".toRegex(), "")
            .toInt()
    }

    private fun getErrorText(error: Exception): String {
        val msg = error.message ?: return "Unknown Error"

        return when (getErrorCode(msg)) {
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