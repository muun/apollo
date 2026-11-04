package io.muun.apollo.domain

import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.muun.apollo.domain.analytics.Analytics
import io.muun.apollo.domain.analytics.AnalyticsEvent
import io.muun.apollo.domain.libwallet.LibwalletClient
import io.muun.apollo.domain.model.MuunFeature
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FeatureOverrideStoreTest {

    companion object {
        private const val NFC_CARD_V2_OVERRIDE_KEY = "featureFlagOverrides:nfcCardV2"
    }

    private val libwalletClient = mockk<LibwalletClient>(relaxed = true)
    private val analytics = mockk<Analytics>(relaxed = true)

    private val store = FeatureOverrideStore(libwalletClient, analytics)

    private val feature = MuunFeature.NFC_CARD_V2.toOverridableFeature()
        as MuunFeature.OverridableFeature.Overridable

    @Test
    fun `disabling a flag should store the override and report the flag as disabled`() {
        store.disableFeatureFlag(feature)

        verify { libwalletClient.saveBoolean(NFC_CARD_V2_OVERRIDE_KEY, true) }
        assertReportedOverride(isEnabled = false)
    }

    @Test
    fun `enabling a flag should clear the override and report the flag as enabled`() {
        store.enableFeatureFlag(feature)

        verify { libwalletClient.saveBoolean(NFC_CARD_V2_OVERRIDE_KEY, false) }
        assertReportedOverride(isEnabled = true)
    }

    private fun assertReportedOverride(isEnabled: Boolean) {
        val event = slot<AnalyticsEvent>()
        verify(exactly = 1) { analytics.report(capture(event)) }

        assertTrue(event.captured is AnalyticsEvent.E_FEATURE_FLAG_OVERRIDE)
        assertEquals("NFC_CARD_V2", event.captured.metadata["name"])
        assertEquals(isEnabled, event.captured.metadata["is_enabled"])
    }
}
