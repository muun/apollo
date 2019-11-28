package io.muun.apollo.domain.errors


import io.muun.apollo.external.UserFacingErrorMessages

class InvalidVerificationCodeError:
    UserFacingError(UserFacingErrorMessages.INSTANCE.invalidVerificationCode())
