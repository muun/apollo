package io.muun.apollo.domain.errors


import io.muun.apollo.data.external.UserFacingErrorMessages

class RecoveryCodeVerificationError
    : UserFacingError(UserFacingErrorMessages.INSTANCE.recoveryCodeVerification()) {

}
