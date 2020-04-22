package io.muun.apollo.domain.libwallet.errors

import io.muun.apollo.domain.errors.MuunError

private var msg = "Libwallet failed to produce a signature"

class LibwalletSigningError : MuunError {

    val tx: String

    constructor(tx: String, cause: Throwable): super(msg, cause) {
        this.tx = tx
        metadata["tx"] = tx
    }
}
