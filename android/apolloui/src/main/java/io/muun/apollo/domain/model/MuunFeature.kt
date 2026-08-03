package io.muun.apollo.domain.model

import io.muun.common.api.MuunFeatureJson
import libwallet.Libwallet

enum class MuunFeature {
    TAPROOT,
    TAPROOT_PREACTIVATION,
    APOLLO_BIOMETRICS,
    HIGH_FEES_HOME_BANNER,
    OS_VERSION_DEPRECATED_FLOW,
    HIGH_FEES_RECEIVE_FLOW,
    EFFECTIVE_FEES_CALCULATION,
    @Deprecated("V1 card firmware removed, use NFC_CARD_V2")
    NFC_CARD,
    NFC_CARD_V2,
    NFC_SENSORS,
    DIAGNOSTIC_MODE,
    SECURITY_CARDS_MARKETPLACE,
    EK_GO_RENDERING,

    UNSUPPORTED_FEATURE;

    companion object {

        fun fromJson(json: MuunFeatureJson): MuunFeature =
            when (json) {
                MuunFeatureJson.TAPROOT -> TAPROOT
                MuunFeatureJson.TAPROOT_PREACTIVATION -> TAPROOT_PREACTIVATION
                MuunFeatureJson.APOLLO_BIOMETRICS -> APOLLO_BIOMETRICS
                MuunFeatureJson.HIGH_FEES_HOME_BANNER -> HIGH_FEES_HOME_BANNER
                MuunFeatureJson.HIGH_FEES_RECEIVE_FLOW -> HIGH_FEES_RECEIVE_FLOW
                MuunFeatureJson.EFFECTIVE_FEES_CALCULATION -> EFFECTIVE_FEES_CALCULATION
                MuunFeatureJson.OS_VERSION_DEPRECATED_FLOW -> OS_VERSION_DEPRECATED_FLOW
                MuunFeatureJson.NFC_CARD -> NFC_CARD
                MuunFeatureJson.NFC_CARD_V2 -> NFC_CARD_V2
                MuunFeatureJson.NFC_SENSORS -> NFC_SENSORS
                MuunFeatureJson.DIAGNOSTIC_MODE -> DIAGNOSTIC_MODE
                MuunFeatureJson.SECURITY_CARDS_MARKETPLACE -> SECURITY_CARDS_MARKETPLACE
                MuunFeatureJson.EK_GO_RENDERING -> EK_GO_RENDERING

                else -> UNSUPPORTED_FEATURE
            }

        fun fromLibwalletModel(name: String): MuunFeature =
            when (name) {
                Libwallet.BackendFeatureTaproot -> TAPROOT
                Libwallet.BackendFeatureTaprootPreactivation -> TAPROOT_PREACTIVATION
                Libwallet.BackendFeatureApolloBiometrics -> APOLLO_BIOMETRICS
                Libwallet.BackendFeatureHighFeesHomeBanner -> HIGH_FEES_HOME_BANNER
                Libwallet.BackendFeatureHighFeesReceiveFlow -> HIGH_FEES_RECEIVE_FLOW
                Libwallet.BackendFeatureEffectiveFeesCalculation -> EFFECTIVE_FEES_CALCULATION
                Libwallet.BackendFeatureOsVersionDeprecatedFlow -> OS_VERSION_DEPRECATED_FLOW
                Libwallet.BackendFeatureNfcCard -> NFC_CARD
                Libwallet.BackendFeatureNfcCardV2 -> NFC_CARD_V2
                Libwallet.BackendFeatureNfcSensors -> NFC_SENSORS
                Libwallet.BackendFeatureDiagnosticMode -> DIAGNOSTIC_MODE
                Libwallet.BackendFeatureSecurityCardsMarketplace -> SECURITY_CARDS_MARKETPLACE
                Libwallet.BackendFeatureEkGoRendering -> EK_GO_RENDERING

                else -> UNSUPPORTED_FEATURE
            }
    }

    fun toJson() =
        when (this) {
            TAPROOT -> MuunFeatureJson.TAPROOT
            TAPROOT_PREACTIVATION -> MuunFeatureJson.TAPROOT_PREACTIVATION
            APOLLO_BIOMETRICS -> MuunFeatureJson.APOLLO_BIOMETRICS
            HIGH_FEES_HOME_BANNER -> MuunFeatureJson.HIGH_FEES_HOME_BANNER
            HIGH_FEES_RECEIVE_FLOW -> MuunFeatureJson.HIGH_FEES_RECEIVE_FLOW
            EFFECTIVE_FEES_CALCULATION -> MuunFeatureJson.EFFECTIVE_FEES_CALCULATION
            OS_VERSION_DEPRECATED_FLOW -> MuunFeatureJson.OS_VERSION_DEPRECATED_FLOW
            NFC_CARD -> MuunFeatureJson.NFC_CARD
            NFC_CARD_V2 -> MuunFeatureJson.NFC_CARD_V2
            NFC_SENSORS -> MuunFeatureJson.NFC_SENSORS
            DIAGNOSTIC_MODE -> MuunFeatureJson.DIAGNOSTIC_MODE
            SECURITY_CARDS_MARKETPLACE -> MuunFeatureJson.SECURITY_CARDS_MARKETPLACE
            EK_GO_RENDERING -> MuunFeatureJson.EK_GO_RENDERING

            UNSUPPORTED_FEATURE -> MuunFeatureJson.UNSUPPORTED_FEATURE
        }

    fun toLibwalletModel(): String =
        when (this) {
            TAPROOT -> Libwallet.BackendFeatureTaproot
            TAPROOT_PREACTIVATION -> Libwallet.BackendFeatureTaprootPreactivation
            APOLLO_BIOMETRICS -> Libwallet.BackendFeatureApolloBiometrics
            HIGH_FEES_HOME_BANNER -> Libwallet.BackendFeatureHighFeesHomeBanner
            HIGH_FEES_RECEIVE_FLOW -> Libwallet.BackendFeatureHighFeesReceiveFlow
            EFFECTIVE_FEES_CALCULATION -> Libwallet.BackendFeatureEffectiveFeesCalculation
            OS_VERSION_DEPRECATED_FLOW -> Libwallet.BackendFeatureOsVersionDeprecatedFlow
            NFC_CARD -> Libwallet.BackendFeatureNfcCard
            NFC_CARD_V2 -> Libwallet.BackendFeatureNfcCardV2
            NFC_SENSORS -> Libwallet.BackendFeatureNfcSensors
            DIAGNOSTIC_MODE -> Libwallet.BackendFeatureDiagnosticMode
            SECURITY_CARDS_MARKETPLACE -> Libwallet.BackendFeatureSecurityCardsMarketplace
            EK_GO_RENDERING -> Libwallet.BackendFeatureEkGoRendering

            UNSUPPORTED_FEATURE -> Libwallet.BackendFeatureUnsupported
        }

    fun isOverridable(): Boolean = toOverridableFeature().isOverridable()

    /**
     * Use this mapping to define whether a feature flag can be overridden locally.
     * - Return `NotOverridable` for flags that must not be overridden locally.
     * - Return `Overridable` for flags that support local overrides.
     *  When using `Overridable`, you MUST provide:
     *  - A human-readable description (shown in the Disable Feature Flags screen)
     *  - A libwallet key name that matches exactly the storage key suffix used by libwallet
     *    (see libwallet/storage/schema.go). The `featureFlagOverrides:` prefix
     *    is automatically added by the storage repository.
     */
    fun toOverridableFeature(): OverridableFeature =
        when (this) {
            TAPROOT -> OverridableFeature.NotOverridable
            TAPROOT_PREACTIVATION -> OverridableFeature.NotOverridable
            APOLLO_BIOMETRICS -> OverridableFeature.NotOverridable
            HIGH_FEES_HOME_BANNER -> OverridableFeature.NotOverridable
            HIGH_FEES_RECEIVE_FLOW -> OverridableFeature.NotOverridable
            EFFECTIVE_FEES_CALCULATION -> OverridableFeature.NotOverridable
            OS_VERSION_DEPRECATED_FLOW -> OverridableFeature.NotOverridable
            NFC_CARD -> OverridableFeature.NotOverridable
            NFC_CARD_V2 -> OverridableFeature.Overridable(
                this,
                "Enables NFC Security Card V2 support",
                "nfcCardV2"
            )

            NFC_SENSORS -> OverridableFeature.NotOverridable
            DIAGNOSTIC_MODE -> OverridableFeature.NotOverridable
            SECURITY_CARDS_MARKETPLACE -> OverridableFeature.NotOverridable
            EK_GO_RENDERING -> OverridableFeature.Overridable(
                this,
                "Enables Go-based emergency kit rendering",
                "ekGoRendering"
            )

            UNSUPPORTED_FEATURE -> OverridableFeature.NotOverridable
        }

    sealed class OverridableFeature {

        // IMPORTANT:
        // If you mark a flag as overridable, you MUST ensure that a matching
        // override key exists in Libwallet Storage and matches exactly (case-sensitive).
        // See libwallet/storage/schema.go.
        // Otherwise, the Disable Feature Flags screen will crash.

        data class Overridable(
            val feature: MuunFeature,
            val humanReadableDesc: String,
            val libwalletKeySuffix: String,
        ) : OverridableFeature()

        object NotOverridable : OverridableFeature()

        fun isOverridable(): Boolean = this is Overridable
    }
}