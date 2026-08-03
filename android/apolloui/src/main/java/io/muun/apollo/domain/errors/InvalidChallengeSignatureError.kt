package io.muun.apollo.domain.errors

class InvalidChallengeSignatureError : MuunError() {
    override val classification = ErrorClassification.EXPECTED // Wrong password/RC
}
