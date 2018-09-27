package io.muun.apollo.domain.errors;


public class EmailAreadyUsedError extends UserFacingError {

    public EmailAreadyUsedError() {
        super("Your e-mail address is already associated with a Muun user");
    }
}
