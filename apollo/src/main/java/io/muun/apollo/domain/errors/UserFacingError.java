package io.muun.apollo.domain.errors;

/**
 * An Exception that will be reported to the user. The message parameter is mandatory to construct
 * one of these, and it will be used by the presentation layer.
 */
public abstract class UserFacingError extends RuntimeException {

    public UserFacingError() {
    }

    public UserFacingError(String message) {
        super(message);
    }

    public UserFacingError(Throwable cause) {
        super(cause);
    }

    public UserFacingError(String message, Throwable cause) {
        super(message, cause);
    }
}
