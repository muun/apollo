package io.muun.apollo.domain.errors;


public class AmountTooSmallError extends UserFacingError {

    public AmountTooSmallError() {
        super("Amount is too small to send");
    }
}
