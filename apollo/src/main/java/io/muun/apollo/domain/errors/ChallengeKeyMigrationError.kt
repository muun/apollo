package io.muun.apollo.domain.errors

class ChallengeKeyMigrationError(cause: Throwable):
    MuunError("Failed to execute challenge key migration", cause)
