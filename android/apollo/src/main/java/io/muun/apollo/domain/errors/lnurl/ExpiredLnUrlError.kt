package io.muun.apollo.domain.errors.lnurl

import io.muun.apollo.domain.errors.MuunError

class ExpiredLnUrlError(message: String, lnUrl: String) : MuunError() {

    init {
        metadata["message"] = message
        metadata["LNURL"] = lnUrl
    }
}
