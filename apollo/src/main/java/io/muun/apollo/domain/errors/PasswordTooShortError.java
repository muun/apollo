package io.muun.apollo.domain.errors;


import io.muun.apollo.external.UserFacingErrorMessages;

public class PasswordTooShortError extends UserFacingError {

    public PasswordTooShortError() {
        super(UserFacingErrorMessages.INSTANCE.passwordTooShort());
    }
}
