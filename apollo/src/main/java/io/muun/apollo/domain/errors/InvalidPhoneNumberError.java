package io.muun.apollo.domain.errors;


import io.muun.apollo.external.UserFacingErrorMessages;

public class InvalidPhoneNumberError extends UserFacingError {


    public InvalidPhoneNumberError() {
        super(UserFacingErrorMessages.INSTANCE.invalidPhoneNumber());
    }

    public InvalidPhoneNumberError(Throwable cause) {
        super(UserFacingErrorMessages.INSTANCE.invalidPhoneNumber(), cause);
    }
}
