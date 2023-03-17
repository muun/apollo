package io.muun.apollo.domain.errors

class InitialSyncError(cause: Throwable) : MuunError(
    "Error during initial loading. Suggestion: Restart the application and try again", // not user visible
    cause
)
