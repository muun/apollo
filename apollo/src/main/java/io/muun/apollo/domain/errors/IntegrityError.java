package io.muun.apollo.domain.errors;


public class IntegrityError extends RuntimeException {

    public IntegrityError(String message) {
        super(message);
    }
}
