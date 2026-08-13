package io.muun.apollo.domain.errors.newop

import io.muun.apollo.domain.errors.ErrorClassification
import io.muun.apollo.domain.errors.MuunError

class InvoiceMissingAmountException(invoice: String, cause: Throwable) : MuunError(invoice, cause) {
    override val classification = ErrorClassification.EXPECTED
}
