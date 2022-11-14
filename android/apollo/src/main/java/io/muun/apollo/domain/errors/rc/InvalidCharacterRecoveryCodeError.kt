package io.muun.apollo.domain.errors.rc

import io.muun.apollo.data.external.UserFacingErrorMessages
import io.muun.apollo.domain.errors.UserFacingError

class InvalidCharacterRecoveryCodeError :
    UserFacingError(UserFacingErrorMessages.INSTANCE.invalidCharacterRecoveryCode())
