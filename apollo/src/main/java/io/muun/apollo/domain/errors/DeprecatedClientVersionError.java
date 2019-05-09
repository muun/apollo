package io.muun.apollo.domain.errors;


import io.muun.apollo.external.UserFacingErrorMessages;

public class DeprecatedClientVersionError extends UserFacingError {

    public DeprecatedClientVersionError() {
        super(UserFacingErrorMessages.INSTANCE.deprecatedClientVersion());
    }
}
