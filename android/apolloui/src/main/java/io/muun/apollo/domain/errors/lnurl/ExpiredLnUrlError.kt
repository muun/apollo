package io.muun.apollo.domain.errors.lnurl

import io.muun.apollo.domain.errors.ErrorClassification
import io.muun.apollo.domain.errors.MuunError

class ExpiredLnUrlError(message: String, lnUrl: String) : MuunError() {

    override val classification = ErrorClassification.EXPECTED

    init {
        metadata["message"] = message
        metadata["LNURL"] = lnUrl
    }
}
