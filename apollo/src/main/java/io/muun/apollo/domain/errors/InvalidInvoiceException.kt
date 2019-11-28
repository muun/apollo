package io.muun.apollo.domain.errors

import io.muun.common.exception.PotentialBug

class InvalidInvoiceException(invoice: String, cause: Throwable):
    MuunError(invoice, cause), PotentialBug
