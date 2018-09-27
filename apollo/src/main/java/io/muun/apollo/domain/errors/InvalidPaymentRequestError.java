package io.muun.apollo.domain.errors;


public class InvalidPaymentRequestError extends UserFacingError {

    private static final String MESSAGE = "The payment link is not valid";

    public InvalidPaymentRequestError() {
        super(MESSAGE);
    }

    public InvalidPaymentRequestError(Throwable cause) {
        super(MESSAGE, cause);
    }

    public InvalidPaymentRequestError(String innerMessage) {
        super(MESSAGE, new RuntimeException(innerMessage));
    }
}
