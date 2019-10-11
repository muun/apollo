package io.muun.apollo.domain.errors

class ChallengeKeyMigrationError(cause: Throwable):
    RuntimeException("Failed to execute challenge key migration", cause)
