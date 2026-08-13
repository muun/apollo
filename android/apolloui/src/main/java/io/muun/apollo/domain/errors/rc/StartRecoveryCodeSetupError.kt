package io.muun.apollo.domain.errors.rc

import io.muun.apollo.domain.errors.ErrorClassification
import io.muun.apollo.domain.errors.MuunError

class StartRecoveryCodeSetupError(cause: Throwable) : MuunError(cause) {
    override val classification = ErrorClassification.UNEXPECTED
}