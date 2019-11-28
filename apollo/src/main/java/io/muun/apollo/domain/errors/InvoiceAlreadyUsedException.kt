package io.muun.apollo.domain.errors

class InvoiceAlreadyUsedException(invoice: String, cause: Throwable):
    MuunError(invoice, cause)
