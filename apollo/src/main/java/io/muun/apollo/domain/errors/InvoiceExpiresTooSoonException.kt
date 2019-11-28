package io.muun.apollo.domain.errors

class InvoiceExpiresTooSoonException(invoice: String, cause: Throwable):
    MuunError(invoice, cause)
