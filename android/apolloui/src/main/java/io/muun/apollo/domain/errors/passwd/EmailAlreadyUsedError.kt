package io.muun.apollo.domain.errors.passwd


import io.muun.apollo.data.external.UserFacingErrorMessages
import io.muun.apollo.domain.errors.ErrorClassification
import io.muun.apollo.domain.errors.UserFacingError

class EmailAlreadyUsedError : UserFacingError(
    UserFacingErrorMessages.INSTANCE.emailAreadyUsed()
) {
    override val classification = ErrorClassification.EXPECTED
}
