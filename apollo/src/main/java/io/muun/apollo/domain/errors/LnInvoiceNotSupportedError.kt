package io.muun.apollo.domain.errors


import io.muun.apollo.external.UserFacingErrorMessages

class LnInvoiceNotSupportedError: UserFacingError {

    constructor():
        super(UserFacingErrorMessages.INSTANCE.lnInvoiceNotSupported())

    constructor(cause: Throwable):
        super(UserFacingErrorMessages.INSTANCE.lnInvoiceNotSupported(), cause)
}
