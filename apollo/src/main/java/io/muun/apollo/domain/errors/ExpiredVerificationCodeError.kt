package io.muun.apollo.domain.errors


import io.muun.apollo.external.UserFacingErrorMessages

class ExpiredVerificationCodeError:
    UserFacingError(UserFacingErrorMessages.INSTANCE.expiredVerificationCode())
