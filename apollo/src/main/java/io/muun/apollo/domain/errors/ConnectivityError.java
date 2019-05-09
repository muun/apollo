package io.muun.apollo.domain.errors;

import io.muun.apollo.external.UserFacingErrorMessages;

public class ConnectivityError extends UserFacingError {

    public ConnectivityError(Throwable cause) {
        super(UserFacingErrorMessages.INSTANCE.connectivity(), cause);
    }
}
