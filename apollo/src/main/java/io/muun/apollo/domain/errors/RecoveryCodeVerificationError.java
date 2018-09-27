package io.muun.apollo.domain.errors;


public class RecoveryCodeVerificationError extends UserFacingError {

    private static final String MESSAGE = "Wrong code. Try again.";

    public RecoveryCodeVerificationError() {
        super(MESSAGE);
    }

    public RecoveryCodeVerificationError(Throwable cause) {
        super(MESSAGE, cause);
    }
}
