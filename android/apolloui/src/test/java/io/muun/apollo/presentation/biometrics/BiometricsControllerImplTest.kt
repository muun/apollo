package io.muun.apollo.presentation.biometrics

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.fragment.app.FragmentActivity
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.verify
import io.muun.apollo.data.external.UserFacingErrorMessages
import io.muun.apollo.data.preferences.BiometricsRepository
import io.muun.apollo.domain.analytics.Analytics
import io.muun.apollo.domain.errors.BiometricAuthenticationError
import io.muun.apollo.domain.model.MuunFeature
import io.muun.apollo.domain.selector.FeatureSelector
import org.junit.Before
import org.junit.Test

class BiometricsControllerImplTest {

    @MockK
    lateinit var applicationContext: Context

    @MockK
    lateinit var biometricsRepository: BiometricsRepository

    @MockK
    lateinit var featureSelector: FeatureSelector

    @MockK
    lateinit var analytics: Analytics

    @MockK
    lateinit var biometricManager: BiometricManager

    lateinit var controller: BiometricsControllerImpl

    @Before
    fun setup() {
        MockKAnnotations.init(this, relaxed = true)

        mockkStatic(BiometricManager::class)
        every { BiometricManager.from(applicationContext) } returns biometricManager
        UserFacingErrorMessages.INSTANCE = mockk<UserFacingErrorMessages>().apply {
            every { biometricsAuthenticationError(any()) } returns ""
        }

        controller = BiometricsControllerImpl(
            applicationContext = applicationContext,
            repository = biometricsRepository,
            featureSel = featureSelector,
            analytics = analytics,
        )
    }

    @Test
    fun authenticationShouldFailWhenFeatureFlagIsNotEnabled() {
        val onSuccess = mockk<() -> Unit>()
        val onFailure = mockk<(BiometricAuthenticationError) -> Unit>().apply {
            every { this@apply.invoke(any()) } just runs
        }
        every { featureSelector.get(MuunFeature.APOLLO_BIOMETRICS) } returns false
        every { biometricManager.canAuthenticate(any()) } returns BiometricManager.BIOMETRIC_SUCCESS

        controller.authenticate(
            mockk<FragmentActivity>(),
            promptTitle = "promptTitle",
            promptSubtitle = "promptSubtitle",
            onSuccess = onSuccess,
            onFailure = onFailure,
        )

        verify(exactly = 0) { onSuccess.invoke() }
        verify(exactly = 1) { onFailure.invoke(any()) }
    }

    @Test
    fun authenticationShouldFailWhenDeviceIsNotCapable() {
        val onSuccess = mockk<() -> Unit>()
        val onFailure = mockk<(BiometricAuthenticationError) -> Unit>().apply {
            every { this@apply.invoke(any()) } just runs
        }
        every { featureSelector.get(MuunFeature.APOLLO_BIOMETRICS) } returns true
        every { biometricManager.canAuthenticate(any()) } returns BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED

        controller.authenticate(
            mockk<FragmentActivity>(),
            promptTitle = "promptTitle",
            promptSubtitle = "promptSubtitle",
            onSuccess = onSuccess,
            onFailure = onFailure,
        )

        verify(exactly = 0) { onSuccess.invoke() }
        verify(exactly = 1) { onFailure.invoke(any()) }
    }
}