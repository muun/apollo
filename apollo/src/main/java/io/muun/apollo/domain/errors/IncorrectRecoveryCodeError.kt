package io.muun.apollo.domain.errors

import io.muun.apollo.external.UserFacingErrorMessages

class IncorrectRecoveryCodeError:
    UserFacingError(UserFacingErrorMessages.INSTANCE.incorrectRecoveryCode())
