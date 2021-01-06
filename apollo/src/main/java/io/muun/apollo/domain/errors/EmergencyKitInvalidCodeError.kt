package io.muun.apollo.domain.errors

import io.muun.apollo.data.external.UserFacingErrorMessages


open class EmergencyKitInvalidCodeError:
    EmergencyKitVerificationError(
        UserFacingErrorMessages.INSTANCE.emergencyKitInvalidVerificationCode()
    )
