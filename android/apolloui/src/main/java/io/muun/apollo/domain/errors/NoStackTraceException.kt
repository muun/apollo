package io.muun.apollo.domain.errors

class NoStackTraceException(message: String) : MuunError(message) {
    override val classification = ErrorClassification.UNEXPECTED
}
