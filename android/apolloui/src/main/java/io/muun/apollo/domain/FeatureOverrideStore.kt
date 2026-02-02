package io.muun.apollo.domain

import io.muun.apollo.domain.analytics.Analytics
import io.muun.apollo.domain.analytics.AnalyticsEvent
import io.muun.apollo.domain.libwallet.LibwalletClient
import io.muun.apollo.domain.model.MuunFeature
import javax.inject.Inject

/**
 * Abstracts dogfood functionality that allows users to manually disable some feature flags.
 * If a MuunFeature is overridden, it is effectively disabled.
 * This works as a selector but also allows writes.
 * NOTE: this currently only supports (and its custom tailored for) some very specific FFs. In the
 * future we'll generalize it.
 */
class FeatureOverrideStore @Inject constructor(
    private val libwalletClient: LibwalletClient,
    private val analytics: Analytics,
) {

    companion object {
        private const val SECURITY_CARD_FEATURE_FLAG_OVERRIDE_KEY = "featureFlagOverrides:nfcCardV2"
    }

    fun getFeatureOverrides(): List<MuunFeature> {
        // For now we only support security card FF
        if (libwalletClient.getBoolean(SECURITY_CARD_FEATURE_FLAG_OVERRIDE_KEY, false)) {
            return listOf(MuunFeature.NFC_CARD_V2)
        } else {
            return listOf()
        }
    }

    fun isOverridden(muunFeature: MuunFeature): Boolean {
        return getFeatureOverrides().contains(muunFeature)
    }

    fun storeOverride(muunFeature: MuunFeature, isOverridden: Boolean) {
        if (muunFeature == MuunFeature.NFC_CARD_V2) {
            libwalletClient.saveBoolean(SECURITY_CARD_FEATURE_FLAG_OVERRIDE_KEY, isOverridden)
            analytics.report(AnalyticsEvent.E_FEATURE_FLAG_OVERRIDE(muunFeature.name, isOverridden))

        } else {
            // We don't support other features yet
            throw UnsupportedOperationException("We don't support overriding this feature")
        }
    }
}