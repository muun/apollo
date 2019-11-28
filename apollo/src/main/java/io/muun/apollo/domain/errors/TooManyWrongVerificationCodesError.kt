package io.muun.apollo.domain.errors


import io.muun.apollo.external.UserFacingErrorMessages

class TooManyWrongVerificationCodesError:
    UserFacingError(UserFacingErrorMessages.INSTANCE.tooManyWrongVerificationCodes())
