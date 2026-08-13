package io.muun.apollo.domain.errors.passwd


import io.muun.apollo.data.external.UserFacingErrorMessages
import io.muun.apollo.domain.errors.ErrorClassification
import io.muun.apollo.domain.errors.UserFacingError

class PasswordTooShortError : UserFacingError(
    UserFacingErrorMessages.INSTANCE.passwordTooShort()
) {
    override val classification = ErrorClassification.EXPECTED
}
