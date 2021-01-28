package io.muun.apollo.domain.errors

class InvoiceExpiredException: MuunError {

    constructor(invoice: String): super(invoice)
    constructor(invoice: String, cause: Throwable): super(invoice, cause)
}
