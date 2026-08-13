package io.muun.apollo.domain.errors

class WrappedErrorMessage(message: String) : MuunError(message) {
    override val classification = ErrorClassification.UNEXPECTED
}