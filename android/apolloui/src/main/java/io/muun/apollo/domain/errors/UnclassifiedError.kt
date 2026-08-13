package io.muun.apollo.domain.errors

/**
 * Generic error for cases where a specific error class doesn't exist.
 * Defaults to UNEXPECTED classification as a safe default.
 */
class UnclassifiedError : MuunError {

    constructor() : super()
    constructor(message: String) : super(message)
    constructor(cause: Throwable) : super(cause)
    constructor(message: String, cause: Throwable) : super(message, cause)

    override val classification = ErrorClassification.UNEXPECTED
}
