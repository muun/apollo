package io.muun.apollo.domain.libwallet.errors

import io.muun.apollo.domain.errors.MuunError


class LibwalletVerificationError(cause: Throwable):
    MuunError("Libwallet rejected the transaction during verification", cause)
