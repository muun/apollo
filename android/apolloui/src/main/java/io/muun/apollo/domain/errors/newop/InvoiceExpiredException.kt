package io.muun.apollo.domain.errors.newop

import io.muun.apollo.domain.errors.MuunError

class InvoiceExpiredException : MuunError {

    constructor(invoice: String) : super(invoice)
    constructor(invoice: String, cause: Throwable) : super(invoice, cause)
}
