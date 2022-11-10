package io.muun.apollo.domain.errors.newop

import io.muun.apollo.domain.errors.MuunError

class InvoiceMissingAmountException(invoice: String, cause: Throwable) : MuunError(invoice, cause)
