package io.muun.apollo.domain.errors;

public class IncorrectRecoveryCodeError extends UserFacingError {

    public IncorrectRecoveryCodeError() {
        super("Incorrect recovery code. Please try again.");
    }
}
