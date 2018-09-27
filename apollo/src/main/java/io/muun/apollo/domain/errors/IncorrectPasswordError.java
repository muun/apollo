package io.muun.apollo.domain.errors;

public class IncorrectPasswordError extends UserFacingError {

    public IncorrectPasswordError() {
        super("Incorrect password. Please try again.");
    }
}
