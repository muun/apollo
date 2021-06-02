package io.muun.apollo.domain.errors.lnurl

import io.muun.apollo.domain.errors.MuunError

class InvalidLnUrlTagError(text: String) : MuunError() {

    init {
        metadata["text"] = text
    }
}
