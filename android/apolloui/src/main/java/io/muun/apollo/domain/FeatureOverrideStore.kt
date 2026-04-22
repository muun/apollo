package io.muun.apollo.domain

import io.muun.apollo.domain.analytics.Analytics
import io.muun.apollo.domain.analytics.AnalyticsEvent
import io.muun.apollo.domain.libwallet.LibwalletClient
import io.muun.apollo.domain.model.MuunFeature
import timber.log.Timber
import javax.inject.Inject

/**
 * Abstracts dogfood functionality that allows users to manually disable some feature flags.
 * If a MuunFeature is overridden, it is effectively disabled.
 * This works as a selector but also allows writes.
 */
class FeatureOverrideStore @Inject constructor(
    private val libwalletClient: LibwalletClient,
    private val analytics: Analytics,
) {

    companion object {
        private const val FEATURE_FLAG_OVERRIDE_PREFIX = "featureFlagOverrides:"
    }

    fun getFeatureOverrides(): List<MuunFeature.OverridableFeature.Overridable> {

        // Check all features for overrides
        val overrides = MuunFeature.entries
            .filter { feature -> feature.isOverridable() }
            .map { muunFeature ->
                muunFeature.toOverridableFeature() as MuunFeature.OverridableFeature.Overridable
            }
            .filter { isOverridden(it) }

        Timber.d("Overridden Feature Flags: ${overrides.joinToString { it.feature.name }}")

        return overrides
    }

    private fun isOverridden(feature: MuunFeature.OverridableFeature.Overridable): Boolean {
        val key = getLibwalletStorageKey(feature)
        return libwalletClient.getBoolean(key, false)
    }

    private fun storeOverride(
        overridableFeature: MuunFeature.OverridableFeature.Overridable,
        isOverridden: Boolean,
    ) {

        val key = getLibwalletStorageKey(overridableFeature)
        libwalletClient.saveBoolean(key, isOverridden)

        val feature = overridableFeature.feature
        analytics.report(AnalyticsEvent.E_FEATURE_FLAG_OVERRIDE(feature.name, isOverridden))
    }

    /**
     * Convenience method. Should be used sparsely and only if you know what you're doing.
     */
    fun disableFeatureFlag(muunFeature: MuunFeature) {
        if (muunFeature.isOverridable()) {
            val overridableFeature = muunFeature.toOverridableFeature()
                as MuunFeature.OverridableFeature.Overridable
            disableFeatureFlag(overridableFeature)

        } else {
            throw IllegalStateException("Not overridable Feature: $muunFeature")
        }
    }

    fun disableFeatureFlag(overridableFeature: MuunFeature.OverridableFeature.Overridable) {
        storeOverride(overridableFeature, true)
    }

    fun enableFeatureFlag(overridableFeature: MuunFeature.OverridableFeature.Overridable) {
        storeOverride(overridableFeature, false)
    }

    private fun getLibwalletStorageKey(
        overridableFeature: MuunFeature.OverridableFeature.Overridable,
    ): String {
        return FEATURE_FLAG_OVERRIDE_PREFIX + overridableFeature.libwalletKeySuffix
    }
}