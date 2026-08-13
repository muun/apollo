package io.muun.apollo.domain.libwallet.errors

import io.muun.apollo.domain.errors.ErrorClassification
import io.muun.apollo.domain.errors.MuunError

private var msg = "Cant fulfill incoming swap"

class UnfulfillableIncomingSwapError(uuid: String, cause: Throwable) : MuunError(msg, cause) {

    override val classification = ErrorClassification.UNEXPECTED

    init {
        metadata["uuid"] = uuid
    }
}