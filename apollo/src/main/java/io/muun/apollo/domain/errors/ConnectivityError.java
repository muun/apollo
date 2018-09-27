package io.muun.apollo.domain.errors;

public class ConnectivityError extends UserFacingError {

    public ConnectivityError(Throwable cause) {
        super("Network error, try again later.", cause);
    }
}
