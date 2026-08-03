package io.muun.apollo.domain.errors.ek

import io.muun.apollo.data.external.UserFacingErrorMessages
import io.muun.apollo.domain.errors.ErrorClassification


class EmergencyKitInvalidCodeError(providedCode: String) : EmergencyKitVerificationError(
    UserFacingErrorMessages.INSTANCE.emergencyKitInvalidVerificationCode()
) {

    override val classification = ErrorClassification.EXPECTED

    init {
        metadata["providedCode"] = providedCode
    }
}
