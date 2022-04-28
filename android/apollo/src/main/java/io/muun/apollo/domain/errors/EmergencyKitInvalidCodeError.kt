package io.muun.apollo.domain.errors

import io.muun.apollo.data.external.UserFacingErrorMessages


class EmergencyKitInvalidCodeError(providedCode: String) : EmergencyKitVerificationError(
    UserFacingErrorMessages.INSTANCE.emergencyKitInvalidVerificationCode()
) {

    init {
        metadata["providedCode"] = providedCode
    }
}
