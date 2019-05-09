package io.muun.apollo.domain.errors;


import io.muun.apollo.external.UserFacingErrorMessages;

public class AmountTooSmallError extends UserFacingError {

    public AmountTooSmallError() {
        super(UserFacingErrorMessages.INSTANCE.amountTooSmall());
    }
}
