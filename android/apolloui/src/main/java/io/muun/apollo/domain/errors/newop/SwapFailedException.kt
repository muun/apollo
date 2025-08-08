package io.muun.apollo.domain.errors.newop

import io.muun.apollo.domain.errors.MuunError

class SwapFailedException(cause: Throwable) : MuunError(cause)
