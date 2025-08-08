package io.muun.apollo.domain.errors.lnurl

import io.muun.apollo.domain.errors.MuunError

class LnUrlServiceUnresponsiveError(domain: String): MuunError() {

    init {
        metadata["service"] = domain
    }
}
