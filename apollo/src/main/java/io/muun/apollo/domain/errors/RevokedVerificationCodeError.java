package io.muun.apollo.domain.errors;

public class RevokedVerificationCodeError extends UserFacingError {

    public RevokedVerificationCodeError() {
        super("This is an old verification code, please use the last one you received");
    }
}