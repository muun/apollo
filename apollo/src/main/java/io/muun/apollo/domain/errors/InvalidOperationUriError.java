package io.muun.apollo.domain.errors;


import io.muun.apollo.domain.model.OperationUri;

public class InvalidOperationUriError extends UserFacingError {

    static final String MESSAGE = "The provided payment details are not valid";

    public InvalidOperationUriError() {
        super(MESSAGE);
    }

    public InvalidOperationUriError(OperationUri uri, Throwable cause) {
        super(MESSAGE, new RuntimeException(uri.toString(), cause));
    }
}
