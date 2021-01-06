package io.muun.apollo.domain.errors

class InvoiceMissingAmountException(invoice: String, cause: Throwable) : MuunError(invoice, cause)
