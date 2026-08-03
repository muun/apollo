package io.muun.apollo.domain.libwallet.errors

import io.muun.apollo.domain.errors.ErrorClassification
import io.muun.apollo.domain.errors.MuunError

class LibwalletEmergencyKitError(cause: Throwable):
    MuunError("Emergency kit generation failed", cause) {
    override val classification = ErrorClassification.UNEXPECTED
}
