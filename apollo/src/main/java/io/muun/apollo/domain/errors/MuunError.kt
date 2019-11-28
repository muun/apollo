package io.muun.apollo.domain.errors

import java.io.Serializable
import java.util.*

open class MuunError: RuntimeException {

    constructor()
    constructor(message: String): super(message)
    constructor(cause: Throwable): super(cause)
    constructor(message: String, cause: Throwable): super(message, cause)

    val metadata = HashMap<String, Serializable>()

    // TODO: generate message using metadata, at least in debug (use Crashlytics keys in prod)

}