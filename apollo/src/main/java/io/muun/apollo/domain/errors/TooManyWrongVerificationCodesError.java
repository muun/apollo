package io.muun.apollo.domain.errors;


public class TooManyWrongVerificationCodesError extends UserFacingError {

    public TooManyWrongVerificationCodesError() {
        super("Too many failed attempts. We're sending you a new code");
    }
}
