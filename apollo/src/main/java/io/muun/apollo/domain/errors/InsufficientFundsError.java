package io.muun.apollo.domain.errors;


public class InsufficientFundsError extends UserFacingError {

    public InsufficientFundsError() {
        super("Insufficient funds for operation");
    }
}
