package io.muun.apollo.domain.errors;


import io.muun.apollo.external.UserFacingErrorMessages;

public class InvalidEmailError extends UserFacingError {

    public InvalidEmailError() {
        super(UserFacingErrorMessages.INSTANCE.invalidEmail());
    }
}
