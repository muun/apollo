package io.muun.apollo.domain.errors;


import io.muun.apollo.external.UserFacingErrorMessages;

public class ExpiredSessionError extends UserFacingError {

    public ExpiredSessionError() {
        super(UserFacingErrorMessages.INSTANCE.expiredSession());
    }
}
