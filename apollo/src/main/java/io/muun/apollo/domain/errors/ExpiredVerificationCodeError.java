package io.muun.apollo.domain.errors;


public class ExpiredVerificationCodeError extends UserFacingError {

    public ExpiredVerificationCodeError() {
        super("Your verification code has expired. We're sending you a new one");
    }
}
