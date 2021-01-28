package io.muun.apollo.domain.errors

import io.muun.apollo.data.external.UserFacingErrorMessages


class EmergencyKitOldCodeError(val firstExpectedDigits: String):
    EmergencyKitVerificationError(
        UserFacingErrorMessages.INSTANCE.emergencyKitOldVerificationCode(firstExpectedDigits)
    )
