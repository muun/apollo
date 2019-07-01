package io.muun.apollo.domain.errors;


import io.muun.apollo.external.UserFacingErrorMessages;
import io.muun.common.exception.PotentialBug;

public class InvalidPaymentRequestError extends UserFacingError implements PotentialBug {

    public InvalidPaymentRequestError(String innerMessage) {
        this(innerMessage, null);
    }

    /**
     * Strange constructor with innerMessage instead of just message.
     */
    public InvalidPaymentRequestError(String innerMessage, Throwable cause) {
        super(
                UserFacingErrorMessages.INSTANCE.invalidPaymentRequest(),
                new RuntimeException(innerMessage, cause)
        );
    }
}
