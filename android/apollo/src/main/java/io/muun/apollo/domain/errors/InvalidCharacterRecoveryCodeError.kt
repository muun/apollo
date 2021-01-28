package io.muun.apollo.domain.errors

import io.muun.apollo.data.external.UserFacingErrorMessages

class InvalidCharacterRecoveryCodeError:
    UserFacingError(UserFacingErrorMessages.INSTANCE.invalidCharacterRecoveryCode())
