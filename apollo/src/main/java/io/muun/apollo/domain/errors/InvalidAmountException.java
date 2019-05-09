package io.muun.apollo.domain.errors;

public class InvalidAmountException extends RuntimeException {

    public InvalidAmountException() {
    }

    public InvalidAmountException(Throwable cause) {
        super(cause);
    }
}
