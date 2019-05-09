package io.muun.apollo.domain.errors;


public class DatabaseError extends RuntimeException {

    public DatabaseError(String message, Throwable cause) {
        super(message, cause);
    }
}
