package io.muun.apollo.domain.errors;

import io.muun.apollo.external.UserFacingErrorMessages;

public class IncorrectPasswordError extends UserFacingError {

    public IncorrectPasswordError() {
        super(UserFacingErrorMessages.INSTANCE.incorrectPassword());
    }
}
