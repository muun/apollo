package io.muun.apollo.domain.errors.newop

import io.muun.apollo.domain.errors.ErrorClassification
import io.muun.apollo.domain.errors.MuunError

class SwapFailedException(cause: Throwable) : MuunError(cause) {
    override val classification = ErrorClassification.UNEXPECTED
}
