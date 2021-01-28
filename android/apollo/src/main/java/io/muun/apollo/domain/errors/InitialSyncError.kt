package io.muun.apollo.domain.errors

class InitialSyncError(cause: Throwable): MuunError(
    "We couldn't load your information. Please, restart the application and try again",
    cause
)
