package io.muun.apollo.domain.errors;


import io.muun.apollo.external.UserFacingErrorMessages;

public class PhoneNumberAlreadyUsedError extends UserFacingError {

    public PhoneNumberAlreadyUsedError() {
        super(UserFacingErrorMessages.INSTANCE.phoneNumberAlreadyUsed());
    }
}
