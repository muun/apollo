package io.muun.apollo.domain.errors;


import io.muun.apollo.external.UserFacingErrorMessages;

public class InvalidPictureError extends UserFacingError {

    public InvalidPictureError() {
        super(UserFacingErrorMessages.INSTANCE.invalidPicture());
    }

    public InvalidPictureError(Throwable cause) {
        super(UserFacingErrorMessages.INSTANCE.invalidPicture(), cause);
    }
}
