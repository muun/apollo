package io.muun.apollo.domain.libwallet.errors

import io.muun.apollo.domain.errors.ErrorClassification
import io.muun.apollo.domain.errors.MuunError

private var msg = "Libwallet failed to decrypt a payload"

class PayloadDecryptError(cause: Throwable) : MuunError(msg, cause) {
    override val classification = ErrorClassification.UNEXPECTED
}
