package io.muun.apollo.domain.errors.ek

import io.muun.apollo.data.external.UserFacingErrorMessages
import io.muun.apollo.domain.errors.ErrorClassification


class EmergencyKitOldCodeError(firstExpectedDigits: String) : EmergencyKitVerificationError(
    UserFacingErrorMessages.INSTANCE.emergencyKitOldVerificationCode(firstExpectedDigits)
) {
    override val classification = ErrorClassification.EXPECTED
}
