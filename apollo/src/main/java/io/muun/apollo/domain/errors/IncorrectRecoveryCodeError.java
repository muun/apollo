package io.muun.apollo.domain.errors;

import io.muun.apollo.external.UserFacingErrorMessages;

public class IncorrectRecoveryCodeError extends UserFacingError {

    public IncorrectRecoveryCodeError() {
        super(UserFacingErrorMessages.INSTANCE.incorrectRecoveryCode());
    }
}
