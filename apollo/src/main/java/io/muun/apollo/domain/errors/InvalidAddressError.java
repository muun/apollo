package io.muun.apollo.domain.errors;


import io.muun.apollo.external.UserFacingErrorMessages;

public class InvalidAddressError extends UserFacingError {

    public InvalidAddressError() {
        super(UserFacingErrorMessages.INSTANCE.invalidAddress());
    }
}
