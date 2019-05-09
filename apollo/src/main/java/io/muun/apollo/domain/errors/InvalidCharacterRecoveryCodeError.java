package io.muun.apollo.domain.errors;

import io.muun.apollo.external.UserFacingErrorMessages;

public class InvalidCharacterRecoveryCodeError extends UserFacingError {

    public InvalidCharacterRecoveryCodeError() {
        super(UserFacingErrorMessages.INSTANCE.invalidCharacterRecoveryCode());
    }
}
