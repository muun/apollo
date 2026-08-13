package io.muun.apollo.domain.errors.newop

import io.muun.apollo.domain.errors.ErrorClassification
import io.muun.apollo.domain.errors.UserFacingError

class PushTransactionSlowError(cause: Throwable) : UserFacingError(cause) {
    override val classification = ErrorClassification.UNEXPECTED
}