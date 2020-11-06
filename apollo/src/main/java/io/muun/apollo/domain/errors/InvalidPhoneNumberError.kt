package io.muun.apollo.domain.errors


import io.muun.apollo.data.external.UserFacingErrorMessages

class InvalidPhoneNumberError: UserFacingError {

    constructor():
        super(UserFacingErrorMessages.INSTANCE.invalidPhoneNumber())

    constructor(cause: Throwable):
        super(UserFacingErrorMessages.INSTANCE.invalidPhoneNumber(), cause)
}
