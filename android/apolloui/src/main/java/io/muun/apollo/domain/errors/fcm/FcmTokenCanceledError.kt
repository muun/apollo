package io.muun.apollo.domain.errors.fcm

import io.muun.apollo.domain.errors.ErrorClassification
import io.muun.apollo.domain.errors.MuunError

class FcmTokenCanceledError : MuunError() {
    override val classification = ErrorClassification.UNEXPECTED
}