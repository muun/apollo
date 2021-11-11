package io.muun.apollo.domain.model

import io.muun.common.MuunFeatureJson
import io.muun.common.exception.MissingCaseError
import libwallet.Libwallet

enum class MuunFeature {
    UNSUPPORTED_FEATURE,
    TAPROOT;

    companion object {

        fun fromJson(json: MuunFeatureJson): MuunFeature =
            when (json) {
                MuunFeatureJson.TAPROOT -> TAPROOT
                else -> throw MissingCaseError(json)
            }

        fun fromLibwalletModel(name: String): MuunFeature =
            when (name) {
                Libwallet.BackendFeatureTaproot -> TAPROOT
                else -> throw MissingCaseError(name, "MuunFeature conversion from libwallet")
            }
    }

    fun toJson() =
        when (this) {
            TAPROOT -> MuunFeatureJson.TAPROOT
            else -> MuunFeatureJson.UNSUPPORTED_FEATURE
        }

    fun toLibwalletModel(): String =
        when (this) {
            TAPROOT -> Libwallet.BackendFeatureTaproot
            else -> throw MissingCaseError(name, "MuunFeature conversion to libwallet")
        }
}