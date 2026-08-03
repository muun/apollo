package io.muun.apollo.domain.errors.integrity

import io.muun.apollo.domain.errors.ErrorClassification
import io.muun.apollo.domain.errors.MuunError


open class IntegrityError(message: String) : MuunError(message) {
    override val classification = ErrorClassification.UNEXPECTED
}
