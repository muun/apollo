package io.muun.apollo.domain.errors.p2p


import io.muun.apollo.data.external.UserFacingErrorMessages
import io.muun.apollo.domain.errors.ErrorClassification
import io.muun.apollo.domain.errors.UserFacingError

class InvalidVerificationCodeError : UserFacingError(
    UserFacingErrorMessages.INSTANCE.invalidVerificationCode()
) {
    override val classification = ErrorClassification.EXPECTED
}
