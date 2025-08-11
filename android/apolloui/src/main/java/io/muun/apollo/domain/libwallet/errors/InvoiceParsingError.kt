package io.muun.apollo.domain.libwallet.errors

import io.muun.apollo.domain.errors.MuunError

private var msg = "Libwallet failed to parse an invoice"

class InvoiceParsingError(val invoice: String, cause: Throwable) : MuunError(msg, cause) {

    init {
        metadata["invoice"] = invoice
    }
}