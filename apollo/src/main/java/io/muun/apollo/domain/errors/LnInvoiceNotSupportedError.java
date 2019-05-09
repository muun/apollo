package io.muun.apollo.domain.errors;


import io.muun.apollo.external.UserFacingErrorMessages;

public class LnInvoiceNotSupportedError extends UserFacingError {

    public LnInvoiceNotSupportedError() {
        super(UserFacingErrorMessages.INSTANCE.lnInvoiceNotSupported());
    }

    public LnInvoiceNotSupportedError(Throwable cause) {
        super(UserFacingErrorMessages.INSTANCE.lnInvoiceNotSupported(), cause);
    }
}
