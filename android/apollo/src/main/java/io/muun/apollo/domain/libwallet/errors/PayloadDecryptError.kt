package io.muun.apollo.domain.libwallet.errors

import io.muun.apollo.domain.errors.MuunError

private var msg = "Libwallet failed to decrypt a payload"

class PayloadDecryptError : MuunError {
    constructor(cause: Throwable): super(msg, cause)
}
