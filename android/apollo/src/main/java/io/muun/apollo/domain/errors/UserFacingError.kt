package io.muun.apollo.domain.errors

/**
 * An Exception that will be reported to the user. The message parameter is mandatory to construct
 * one of these, and it will be used by the presentation layer.
 */
abstract class UserFacingError : MuunError {

    constructor()
    constructor(message: String) : super(message)
    constructor(cause: Throwable) : super(cause)
    constructor(message: String, cause: Throwable) : super(message, cause)
}
