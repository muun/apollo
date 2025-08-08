package io.muun.apollo.domain.errors

class InitialSyncNetworkError(cause: Throwable) : MuunError(
    "Connection error during initial loading. Suggestion: Restart the application and try again", // not user visible
    cause
)
