package io.muun.apollo.domain.errors;

import io.muun.apollo.external.UserFacingErrorMessages;

public class RevokedVerificationCodeError extends UserFacingError {

    public RevokedVerificationCodeError() {
        super(UserFacingErrorMessages.INSTANCE.revokedVerificationCode());
    }
}