package io.muun.apollo.domain.errors.newop

import io.muun.apollo.domain.errors.ErrorClassification
import io.muun.apollo.domain.errors.MuunError

class InvalidOperationUriException(message: String) : MuunError(message) {
    override val classification = ErrorClassification.UNEXPECTED
}
