package io.muun.apollo.domain.errors.lnurl

import io.muun.apollo.domain.errors.MuunError

class ExpiredLnUrlInvoiceError(domain: String, invoice: String) : MuunError() {

    init {
        metadata["service"] = domain
        metadata["invoice"] = invoice
    }
}
