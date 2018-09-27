package io.muun.apollo.domain.errors;


public class DeprecatedClientVersionError extends UserFacingError {

    public DeprecatedClientVersionError() {
        super("This version is no longer supported by Muun. Please, update the application");
    }
}
