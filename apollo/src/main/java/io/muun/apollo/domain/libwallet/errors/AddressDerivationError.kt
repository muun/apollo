package io.muun.apollo.domain.libwallet.errors

import io.muun.apollo.domain.errors.MuunError

private var msg = "Libwallet failed to derive an address"

class AddressDerivationError : MuunError {

    val version: Int
    val path: String

    constructor(version: Int, path: String, cause: Throwable): super(msg, cause) {
        this.version = version
        this.path = path
        metadata["version"] = version
        metadata["path"] = path
    }
}
