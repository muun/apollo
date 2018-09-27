package io.muun.apollo.domain.errors;


public class InvalidEmailError extends UserFacingError {

    public InvalidEmailError() {
        super("Invalid email address");
    }
}
