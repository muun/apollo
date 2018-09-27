package io.muun.apollo.domain.errors;


public class InvalidPhoneNumberError extends UserFacingError {

    private static final String message = "Invalid phone number";

    public InvalidPhoneNumberError() {
        super(message);
    }

    public InvalidPhoneNumberError(Throwable cause) {
        super(message, cause);
    }
}
