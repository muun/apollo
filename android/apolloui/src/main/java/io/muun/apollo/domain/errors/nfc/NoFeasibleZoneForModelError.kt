package io.muun.apollo.domain.errors.nfc

import io.muun.apollo.domain.errors.ErrorClassification
import io.muun.apollo.domain.errors.MuunError

class NoFeasibleZoneForModelError : MuunError() {
    override val classification = ErrorClassification.EXPECTED
}
