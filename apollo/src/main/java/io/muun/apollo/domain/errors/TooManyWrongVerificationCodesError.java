package io.muun.apollo.domain.errors;


import io.muun.apollo.external.UserFacingErrorMessages;

public class TooManyWrongVerificationCodesError extends UserFacingError {

    public TooManyWrongVerificationCodesError() {
        super(UserFacingErrorMessages.INSTANCE.tooManyWrongVerificationCodes());
    }
}
