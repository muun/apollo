package io.muun.apollo.domain.errors


class ErrorProcessingError(message: String, cause: Throwable):
    MuunError(message, cause)
