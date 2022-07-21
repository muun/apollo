package io.muun.apollo.domain.model

import io.muun.common.api.MuunFeatureJson
import io.muun.common.exception.MissingCaseError
import libwallet.Libwallet

enum class MuunFeature {
    TAPROOT,
    TAPROOT_PREACTIVATION,
    APOLLO_BIOMETRICS;

    companion object {

        fun fromJson(json: MuunFeatureJson): MuunFeature =
            when (json) {
                MuunFeatureJson.TAPROOT -> TAPROOT
                MuunFeatureJson.TAPROOT_PREACTIVATION -> TAPROOT_PREACTIVATION
                MuunFeatureJson.APOLLO_BIOMETRICS -> APOLLO_BIOMETRICS
                else -> throw MissingCaseError(json)
            }

        fun fromLibwalletModel(name: String): MuunFeature =
            when (name) {
                Libwallet.BackendFeatureTaproot -> TAPROOT
                Libwallet.BackendFeatureTaprootPreactivation -> TAPROOT_PREACTIVATION
                Libwallet.BackendFeatureApolloBiometrics -> APOLLO_BIOMETRICS
                else -> throw MissingCaseError(name, "MuunFeature conversion from libwallet")
            }
    }

    fun toJson() =
        when (this) {
            TAPROOT -> MuunFeatureJson.TAPROOT
            TAPROOT_PREACTIVATION -> MuunFeatureJson.TAPROOT_PREACTIVATION
            APOLLO_BIOMETRICS -> MuunFeatureJson.APOLLO_BIOMETRICS
            else -> MuunFeatureJson.UNSUPPORTED_FEATURE
        }

    fun toLibwalletModel(): String =
        when (this) {
            TAPROOT -> Libwallet.BackendFeatureTaproot
            TAPROOT_PREACTIVATION -> Libwallet.BackendFeatureTaprootPreactivation
            APOLLO_BIOMETRICS -> Libwallet.BackendFeatureApolloBiometrics
            else -> throw MissingCaseError(name, "MuunFeature conversion to libwallet")
        }
}