package io.muun.apollo.domain.errors


import io.muun.common.exception.PotentialBug

class WeirdIncorrectAttemptsBugError(
    remainingAttempts: Int,
    maxAttempts: Int,
) : MuunError(
    "IncorrectAttempts, in secure storage, was found to be higher or equal than max attempts"
), PotentialBug {

    init {
        metadata["remainingAttempts"] = remainingAttempts
        metadata["maxAttempts"] = maxAttempts
        metadata["incorrectAttempts"] = maxAttempts - remainingAttempts

    }
}
