package io.muun.apollo.domain.libwallet.errors

import io.muun.apollo.domain.errors.MuunError

private var msg = "Libwallet failed to parse an invoice"

class InvoiceParsingError : MuunError {

    val invoice: String

    constructor(invoice: String, cause: Throwable): super(msg, cause) {
        this.invoice = invoice
        metadata["invoice"] = invoice
    }
}