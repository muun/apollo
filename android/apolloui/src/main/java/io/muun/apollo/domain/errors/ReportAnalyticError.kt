package io.muun.apollo.domain.errors

class ReportAnalyticError(message: String) : MuunError(message) {
    override val classification = ErrorClassification.UNEXPECTED
}