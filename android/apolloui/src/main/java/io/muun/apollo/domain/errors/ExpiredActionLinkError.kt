package io.muun.apollo.domain.errors

class ExpiredActionLinkError : MuunError() {
    override val classification = ErrorClassification.EXPECTED
}
