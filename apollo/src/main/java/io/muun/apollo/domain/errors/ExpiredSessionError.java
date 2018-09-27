package io.muun.apollo.domain.errors;


public class ExpiredSessionError extends UserFacingError {

    public ExpiredSessionError() {
        super("Your session with Muun has expired. Please, log in again");
    }
}
