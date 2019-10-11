package io.muun.apollo.domain.errors;

public class MissingMigrationError extends RuntimeException {

    public MissingMigrationError(String message) {
        super(message);
    }
}
