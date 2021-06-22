package io.muun.apollo.domain.libwallet.errors

import io.muun.apollo.domain.errors.MuunError

private var msg = "Libwallet failed to encrypt a payload"

class PayloadEncryptError(cause: Throwable) : MuunError(msg, cause)
