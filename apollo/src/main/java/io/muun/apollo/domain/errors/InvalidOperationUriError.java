package io.muun.apollo.domain.errors;


import io.muun.apollo.domain.model.OperationUri;
import io.muun.apollo.external.UserFacingErrorMessages;

public class InvalidOperationUriError extends UserFacingError {

    public final OperationUri uri;

    public InvalidOperationUriError(OperationUri uri, Throwable cause) {
        super(UserFacingErrorMessages.INSTANCE.invalidOperationUri(), cause);
        this.uri = uri;
    }
}
