package io.muun.apollo.domain.errors.fcm

import io.muun.apollo.domain.errors.MuunError

class FcmTokenError(cause: Throwable) : MuunError(cause)
