package io.muun.apollo.data.net.base;

import io.muun.apollo.domain.errors.ErrorClassification;
import io.muun.apollo.domain.errors.MuunError;

import org.jetbrains.annotations.NotNull;

public class NetworkException extends MuunError {

    @NotNull
    @Override
    public ErrorClassification getClassification() {
        return ErrorClassification.UNEXPECTED;
    }

    public NetworkException(String url, Throwable cause) {
        super("Can't reach " + url, cause);
    }

    public NetworkException(Throwable cause) {
        super("Can't reach the remote server", cause);
    }
}
