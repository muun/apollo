package io.muun.apollo.domain.errors


import io.muun.apollo.data.external.UserFacingErrorMessages

class PhoneNumberAlreadyUsedError:
    UserFacingError(UserFacingErrorMessages.INSTANCE.phoneNumberAlreadyUsed())
