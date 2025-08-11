package io.muun.apollo.domain.model

import io.muun.common.exception.MissingCaseError
import libwallet.Libwallet

enum class UserActivatedFeatureStatus {
    OFF,
    CAN_PREACTIVATE,
    CAN_ACTIVATE,
    PREACTIVATED,
    SCHEDULED_ACTIVATION,
    ACTIVE;

    companion object {
        fun fromLibwalletModel(value: String): UserActivatedFeatureStatus =
            when (value) {
                Libwallet.UserActivatedFeatureStatusOff -> OFF
                Libwallet.UserActivatedFeatureStatusCanPreactivate -> CAN_PREACTIVATE
                Libwallet.UserActivatedFeatureStatusCanActivate -> CAN_ACTIVATE
                Libwallet.UserActivatedFeatureStatusPreactivated -> PREACTIVATED
                Libwallet.UserActivatedFeatureStatusScheduledActivation -> SCHEDULED_ACTIVATION
                Libwallet.UserActivatedFeatureStatusActive -> ACTIVE
                else -> throw MissingCaseError(value, "TaprootFeatureStatus from libwallet")
            }
    }

    fun toLibwalletModel(): String =
        when (this) {
            OFF -> Libwallet.UserActivatedFeatureStatusOff
            CAN_PREACTIVATE -> Libwallet.UserActivatedFeatureStatusCanPreactivate
            CAN_ACTIVATE -> Libwallet.UserActivatedFeatureStatusCanActivate
            PREACTIVATED -> Libwallet.UserActivatedFeatureStatusPreactivated
            SCHEDULED_ACTIVATION -> Libwallet.UserActivatedFeatureStatusScheduledActivation
            ACTIVE -> Libwallet.UserActivatedFeatureStatusActive
        }
}