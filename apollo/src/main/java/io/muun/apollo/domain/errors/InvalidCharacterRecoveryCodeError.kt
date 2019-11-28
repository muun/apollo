package io.muun.apollo.domain.errors

import io.muun.apollo.external.UserFacingErrorMessages

class InvalidCharacterRecoveryCodeError:
    UserFacingError(UserFacingErrorMessages.INSTANCE.invalidCharacterRecoveryCode())
