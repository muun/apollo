package io.muun.apollo.domain.errors;


import io.muun.apollo.external.UserFacingErrorMessages;

public class InsufficientFundsError extends UserFacingError {

    public InsufficientFundsError() {
        super(UserFacingErrorMessages.INSTANCE.insufficientFunds());
    }
}
