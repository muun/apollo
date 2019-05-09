package io.muun.apollo.domain.errors;


import io.muun.apollo.external.UserFacingErrorMessages;

public class ExpiredVerificationCodeError extends UserFacingError {

    public ExpiredVerificationCodeError() {
        super(UserFacingErrorMessages.INSTANCE.expiredVerificationCode());
    }
}
