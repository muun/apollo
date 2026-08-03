package io.muun.apollo.domain.errors.ek

import io.muun.apollo.domain.errors.ErrorClassification
import io.muun.apollo.domain.errors.UserFacingError


open class EmergencyKitVerificationError(message: String) : UserFacingError(message) {
    override val classification = ErrorClassification.EXPECTED
}
