package io.muun.apollo.domain.errors

class InvalidJsonError(cause: Throwable) : MuunError(cause) {
    override val classification = ErrorClassification.UNEXPECTED
}
