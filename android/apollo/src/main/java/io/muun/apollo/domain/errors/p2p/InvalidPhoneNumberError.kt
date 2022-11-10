package io.muun.apollo.domain.errors.p2p


import io.muun.apollo.data.external.UserFacingErrorMessages
import io.muun.apollo.domain.errors.UserFacingError

class InvalidPhoneNumberError : UserFacingError {

    constructor() :
        super(UserFacingErrorMessages.INSTANCE.invalidPhoneNumber())

    constructor(cause: Throwable) :
        super(UserFacingErrorMessages.INSTANCE.invalidPhoneNumber(), cause)
}
