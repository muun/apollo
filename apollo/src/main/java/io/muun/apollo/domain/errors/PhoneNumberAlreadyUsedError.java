package io.muun.apollo.domain.errors;


public class PhoneNumberAlreadyUsedError extends UserFacingError {

    public PhoneNumberAlreadyUsedError() {
        super("Your phone number is already associated with a Muun user");
    }
}
