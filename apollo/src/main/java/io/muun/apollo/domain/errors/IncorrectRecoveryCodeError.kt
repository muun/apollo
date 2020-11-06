package io.muun.apollo.domain.errors

import io.muun.apollo.data.external.UserFacingErrorMessages

class IncorrectRecoveryCodeError:
    UserFacingError(UserFacingErrorMessages.INSTANCE.incorrectRecoveryCode())
