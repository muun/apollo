package io.muun.apollo.domain.errors

class BugDetected: MuunError {

    constructor(message: String): super(message)
    constructor(cause: Throwable): super(cause)
    constructor(message: String, cause: Throwable): super(message, cause)

}