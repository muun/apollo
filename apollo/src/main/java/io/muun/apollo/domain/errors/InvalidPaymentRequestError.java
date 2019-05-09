package io.muun.apollo.domain.errors;


import io.muun.apollo.external.UserFacingErrorMessages;

public class InvalidPaymentRequestError extends UserFacingError {


    public InvalidPaymentRequestError() {
        super(UserFacingErrorMessages.INSTANCE.invalidPaymentRequest());
    }

    public InvalidPaymentRequestError(Throwable cause) {
        super(UserFacingErrorMessages.INSTANCE.invalidPaymentRequest(), cause);
    }

    /**
     * Strange constructor with innerMessage instead of just message.
     */
    public InvalidPaymentRequestError(String innerMessage) {
        super(
                UserFacingErrorMessages.INSTANCE.invalidPaymentRequest(),
                new RuntimeException(innerMessage)
        );
    }
}
