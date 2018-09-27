package io.muun.apollo.domain.errors;


public class InvalidAddressError extends UserFacingError {

    public InvalidAddressError() {
        super("The given Bitcoin address is not valid");
    }
}
