package io.muun.apollo.domain.errors.rc

import io.muun.apollo.data.external.UserFacingErrorMessages
import io.muun.apollo.domain.errors.ErrorClassification
import io.muun.apollo.domain.errors.UserFacingError

class InvalidCharacterRecoveryCodeError :
    UserFacingError(UserFacingErrorMessages.INSTANCE.invalidCharacterRecoveryCode()) {
    override val classification = ErrorClassification.EXPECTED
}
