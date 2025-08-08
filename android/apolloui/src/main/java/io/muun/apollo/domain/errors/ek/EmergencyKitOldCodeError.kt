package io.muun.apollo.domain.errors.ek

import io.muun.apollo.data.external.UserFacingErrorMessages


class EmergencyKitOldCodeError(firstExpectedDigits: String) : EmergencyKitVerificationError(
    UserFacingErrorMessages.INSTANCE.emergencyKitOldVerificationCode(firstExpectedDigits)
)
