package io.muun.apollo.domain.errors;

public class NoStackTraceException extends RuntimeException {
    public NoStackTraceException(String message) {
        super(message);
    }
}
