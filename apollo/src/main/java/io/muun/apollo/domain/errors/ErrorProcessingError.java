package io.muun.apollo.domain.errors;


public class ErrorProcessingError extends RuntimeException {

    public ErrorProcessingError(String message, Throwable cause) {
        super(message, cause);
    }
}
