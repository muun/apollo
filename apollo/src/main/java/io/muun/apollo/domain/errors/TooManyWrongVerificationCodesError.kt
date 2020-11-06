package io.muun.apollo.domain.errors


import io.muun.apollo.data.external.UserFacingErrorMessages

class TooManyWrongVerificationCodesError:
    UserFacingError(UserFacingErrorMessages.INSTANCE.tooManyWrongVerificationCodes())
