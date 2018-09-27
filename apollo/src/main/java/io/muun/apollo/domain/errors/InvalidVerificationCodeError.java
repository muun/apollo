package io.muun.apollo.domain.errors;


public class InvalidVerificationCodeError extends UserFacingError {

    public InvalidVerificationCodeError() {
        super("That is not the verification code we sent. Try entering it again");
    }
}
