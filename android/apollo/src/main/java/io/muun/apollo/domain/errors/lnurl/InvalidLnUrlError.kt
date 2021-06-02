package io.muun.apollo.domain.errors.lnurl

import io.muun.apollo.domain.errors.MuunError

class InvalidLnUrlError(text: String) : MuunError() {

    init {
        metadata["text"] = text
    }
}
