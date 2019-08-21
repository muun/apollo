package io.muun.apollo.domain.errors;


import io.muun.apollo.external.UserFacingErrorMessages;

public class PasswordsDontMatchError extends UserFacingError {

    public PasswordsDontMatchError() {
        super(UserFacingErrorMessages.INSTANCE.passwordsDontMatch());
    }
}
