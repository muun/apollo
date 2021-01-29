package io.muun.apollo.domain.libwallet.errors

import io.muun.apollo.domain.errors.MuunError

private var msg = "Cant fulfill incoming swap"

class UnfulfillableIncomingSwapError: MuunError {

    constructor(uuid: String, cause: Throwable) : super(msg, cause) {
        metadata["uuid"] = uuid
    }
}