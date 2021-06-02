package io.muun.apollo.domain.errors.lnurl

import io.muun.apollo.domain.errors.MuunError
import io.muun.apollo.domain.model.lnurl.LnUrlEvent

class UnknownLnUrlError(event: LnUrlEvent) : MuunError() {

    init {
        metadata["code"] = event.code
        metadata["message"] = event.message
        metadata["metadata"] = event.metadata
    }
}
