package io.muun.apollo.domain.errors;


import io.muun.apollo.external.UserFacingErrorMessages;

public class EmailAreadyUsedError extends UserFacingError {

    public EmailAreadyUsedError() {
        super(UserFacingErrorMessages.INSTANCE.emailAreadyUsed());
    }
}
