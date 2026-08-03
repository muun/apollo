package io.muun.apollo.domain.errors

class MissingMigrationError(message: String) : MuunError(message) {
    override val classification = ErrorClassification.UNEXPECTED
}
