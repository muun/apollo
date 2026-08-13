package io.muun.apollo.domain.errors.lnurl

import io.muun.apollo.domain.errors.ErrorClassification
import io.muun.apollo.domain.errors.MuunError

class InvalidLnUrlError(text: String) : MuunError() {

    override val classification = ErrorClassification.EXPECTED

    init {
        metadata["text"] = text
    }
}
