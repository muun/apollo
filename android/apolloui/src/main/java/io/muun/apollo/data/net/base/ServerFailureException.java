package io.muun.apollo.data.net.base;

import io.muun.apollo.domain.errors.ErrorClassification;
import io.muun.apollo.domain.errors.MuunError;

import org.jetbrains.annotations.NotNull;

public class ServerFailureException extends MuunError {

    @NotNull
    @Override
    public ErrorClassification getClassification() {
        return ErrorClassification.UNEXPECTED;
    }

    public ServerFailureException(Throwable cause) {
        super("We're facing a temporary issue. Please, try again later", cause);
    }
}
