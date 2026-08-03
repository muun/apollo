package io.muun.apollo.domain.errors.lnurl

import io.muun.apollo.domain.errors.ErrorClassification
import io.muun.apollo.domain.errors.MuunError

class LnUrlServiceUnresponsiveError(domain: String): MuunError() {

    override val classification = ErrorClassification.UNEXPECTED

    init {
        metadata["service"] = domain
    }
}
