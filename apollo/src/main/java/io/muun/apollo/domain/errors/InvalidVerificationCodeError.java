package io.muun.apollo.domain.errors;


import io.muun.apollo.external.UserFacingErrorMessages;

public class InvalidVerificationCodeError extends UserFacingError {

    public InvalidVerificationCodeError() {
        super(UserFacingErrorMessages.INSTANCE.invalidVerificationCode());
    }
}
