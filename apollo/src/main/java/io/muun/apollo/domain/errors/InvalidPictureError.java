package io.muun.apollo.domain.errors;


public class InvalidPictureError extends UserFacingError {

    private static final String MESSAGE = "The image could not be read";

    public InvalidPictureError() {
        super(MESSAGE);
    }

    public InvalidPictureError(Throwable cause) {
        super(MESSAGE, cause);
    }
}
