package io.muun.apollo.domain.errors


import io.muun.apollo.external.UserFacingErrorMessages

class RecoveryCodeVerificationError: UserFacingError {

    constructor():
        super(UserFacingErrorMessages.INSTANCE.recoveryCodeVerification())

    constructor(cause: Throwable):
        super(UserFacingErrorMessages.INSTANCE.recoveryCodeVerification(), cause)

}
