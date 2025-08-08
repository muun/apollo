package io.muun.apollo.domain.libwallet.errors

import io.muun.apollo.domain.errors.MuunError

class UnknownRecoveryCodeVersionError(cause: Throwable):
    MuunError("Libwallet failed to recognize version from Recovery Code", cause)