package io.muun.apollo.domain.errors.newop

import io.muun.apollo.domain.errors.MuunError
import io.muun.common.exception.PotentialBug

class InvalidInvoiceException(invoice: String, cause: Throwable) : MuunError(
    invoice,
    cause
), PotentialBug
