package io.muun.apollo.domain.errors;


public class PasswordTooShortError extends UserFacingError {

    public PasswordTooShortError() {
        super("Password is too short");
    }
}
