package io.muun.apollo.domain.model

import io.muun.common.api.MuunFeatureJson
import io.muun.common.exception.MissingCaseError
import libwallet.Libwallet

enum class MuunFeature {
    TAPROOT,
    TAPROOT_PREACTIVATION,
    APOLLO_BIOMETRICS,
    HIGH_FEES_HOME_BANNER,
    OS_VERSION_DEPRECATED_FLOW,
    HIGH_FEES_RECEIVE_FLOW,
    EFFECTIVE_FEES_CALCULATION,
    NFC_CARD,
    NFC_SENSORS;

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
                MuunFeatureJson.NFC_SENSORS -> NFC_SENSORS
                else -> throw MissingCaseError(json)
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
                Libwallet.BackendFeatureNfcSensors -> NFC_SENSORS

                else -> throw MissingCaseError(name, "MuunFeature conversion from libwallet")
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
            NFC_SENSORS -> MuunFeatureJson.NFC_SENSORS
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
            NFC_SENSORS -> Libwallet.BackendFeatureNfcSensors
        }
}