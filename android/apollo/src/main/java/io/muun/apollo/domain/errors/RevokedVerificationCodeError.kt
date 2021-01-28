package io.muun.apollo.domain.errors

import io.muun.apollo.data.external.UserFacingErrorMessages

class RevokedVerificationCodeError:
    UserFacingError(UserFacingErrorMessages.INSTANCE.revokedVerificationCode())