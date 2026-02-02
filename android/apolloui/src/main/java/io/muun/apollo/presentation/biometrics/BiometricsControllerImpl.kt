package io.muun.apollo.presentation.biometrics

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.biometric.BiometricPrompt.PromptInfo
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import io.muun.apollo.R
import io.muun.apollo.data.preferences.BiometricsRepository
import io.muun.apollo.domain.analytics.Analytics
import io.muun.apollo.domain.analytics.AnalyticsEvent.E_BIOMETRICS_AUTH_ERROR
import io.muun.apollo.domain.analytics.AnalyticsEvent.E_BIOMETRICS_AUTH_SUCCESS
import io.muun.apollo.domain.analytics.AnalyticsEvent.S_BIOMETRICS_AUTH
import io.muun.apollo.domain.errors.BiometricAuthenticationError
import io.muun.apollo.domain.model.BiometricAuthenticationErrorReason
import io.muun.apollo.domain.model.MuunFeature
import io.muun.apollo.domain.selector.FeatureSelector
import javax.inject.Inject

class BiometricsControllerImpl @Inject constructor(
    private val applicationContext: Context,
    private val repository: BiometricsRepository,
    private val featureSel: FeatureSelector,
    private val analytics: Analytics,
) : BiometricsController, BiometricPrompt.AuthenticationCallback() {

    override fun hasUserOptedInBiometrics(): Boolean {
        return repository.userOptInBiometrics()
    }

    override fun setUserOptInBiometrics(optIn: Boolean) {
        repository.setUserOptInBiometrics(optIn)
    }

    override fun getAuthenticationStatus(): BiometricsAuthenticationStatus {
        fun Int.toAuthenticationStatusString(): String {
            return when (this) {
                BiometricManager.BIOMETRIC_SUCCESS -> "BIOMETRIC_SUCCESS"
                BiometricManager.BIOMETRIC_STATUS_UNKNOWN -> "BIOMETRIC_STATUS_UNKNOWN"
                BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED -> "BIOMETRIC_ERROR_UNSUPPORTED"
                BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> "BIOMETRIC_ERROR_HW_UNAVAILABLE"
                BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> "BIOMETRIC_ERROR_NONE_ENROLLED"
                BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> "BIOMETRIC_ERROR_NO_HARDWARE"
                BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED -> "BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED"
                else -> toString()
            }
        }

        if (!featureSel.get(MuunFeature.APOLLO_BIOMETRICS)) {
            return BiometricsAuthenticationStatus.disabled("FEATURE_FLAG")
        }

        val biometricsManager = BiometricManager.from(applicationContext)
        return when (val result =
            biometricsManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)) {
            BiometricManager.BIOMETRIC_SUCCESS -> BiometricsAuthenticationStatus.enabled()
            else -> BiometricsAuthenticationStatus.disabled(result.toAuthenticationStatusString())
        }
    }

    override fun authenticate(
        activity: FragmentActivity,
        promptTitle: CharSequence,
        promptSubtitle: CharSequence,
        onSuccess: () -> Unit,
        onFailure: (BiometricAuthenticationError) -> Unit,
    ) {
        if (!getAuthenticationStatus().canAuthenticate) {
            onFailure(BiometricAuthenticationError(BiometricAuthenticationErrorReason.GENERAL))
            return
        }

        val executor = ContextCompat.getMainExecutor(applicationContext)
        val biometricPrompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback()  {

            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                repository.setUserOptInBiometrics(true)
                onSuccess()
                analytics.report(E_BIOMETRICS_AUTH_SUCCESS())
            }

            override fun onAuthenticationError(errorCode: Int, errorString: CharSequence) {
                super.onAuthenticationError(errorCode, errorString)
                if (shouldReportFailure(errorCode)) {
                    onFailure(BiometricAuthenticationError(errorCode.toAuthenticationFailedReason()))
                    analytics.report(E_BIOMETRICS_AUTH_ERROR(errorCode.toString(), errorString.toString()))
                }
            }
        })
        val promptInfo = PromptInfo.Builder()
            .setTitle(promptTitle)
            .setSubtitle(promptSubtitle)
            .setNegativeButtonText(applicationContext.getString(R.string.biometrics_cancel))
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build()
        biometricPrompt.authenticate(promptInfo)
        analytics.report(S_BIOMETRICS_AUTH())
    }

    /**
     * Converts [BiometricPrompt.AuthenticationError] to [BiometricAuthenticationErrorReason] domain object.
     */
    private fun @receiver:BiometricPrompt.AuthenticationError Int.toAuthenticationFailedReason(): BiometricAuthenticationErrorReason {
        return when (this) {
            BiometricPrompt.ERROR_LOCKOUT -> BiometricAuthenticationErrorReason.LOCKOUT
            BiometricPrompt.ERROR_LOCKOUT_PERMANENT -> BiometricAuthenticationErrorReason.LOCKOUT_PERMANENT
            else -> BiometricAuthenticationErrorReason.GENERAL
        }
    }

    /**
     * Returns if [BiometricsController] should call [authenticate]'s `onFailure` lambda.
     */
    private fun shouldReportFailure(@BiometricPrompt.AuthenticationError errorCode: Int) =
        !setOf(BiometricPrompt.ERROR_USER_CANCELED, BiometricPrompt.ERROR_CANCELED).contains(errorCode)
}
