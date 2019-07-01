package io.muun.apollo.domain.errors;


import io.muun.apollo.external.UserFacingErrorMessages;

public class FcmTokenNotAvailableError extends UserFacingError {

    public FcmTokenNotAvailableError() {
        super(UserFacingErrorMessages.INSTANCE.fcmTokenNotAvailable());
    }
}
