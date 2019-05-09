package io.muun.apollo.domain.errors;


import io.muun.apollo.external.UserFacingErrorMessages;

public class RecoveryCodeVerificationError extends UserFacingError {


    public RecoveryCodeVerificationError() {
        super(UserFacingErrorMessages.INSTANCE.recoveryCodeVerification());
    }

    public RecoveryCodeVerificationError(Throwable cause) {
        super(UserFacingErrorMessages.INSTANCE.recoveryCodeVerification(), cause);
    }
}
