package io.muun.apollo.domain.libwallet.errors

import io.muun.apollo.domain.errors.MuunError

class InvalidRecoveryCodeFormatError(cause: Throwable) : MuunError(cause)
