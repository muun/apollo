package io.muun.apollo.domain.libwallet.errors

import io.muun.apollo.domain.errors.MuunError

private var msg = "Libwallet failed to produce a signature"

class LibwalletSigningError(val tx: String, cause: Throwable) : MuunError(msg, cause) {

    init {
        metadata["tx"] = tx
    }
}
