package io.muun.apollo.domain.errors


import io.muun.apollo.data.external.UserFacingErrorMessages

class ExpiredVerificationCodeError:
    UserFacingError(UserFacingErrorMessages.INSTANCE.expiredVerificationCode())
