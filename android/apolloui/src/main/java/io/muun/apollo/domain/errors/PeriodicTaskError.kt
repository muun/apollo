package io.muun.apollo.domain.errors

class PeriodicTaskError(taskName: String, duration: Long, cause: Throwable) : MuunError(cause) {

    init {
        metadata["task"] = taskName
        metadata["duration(secs)"] = duration
        metadata["causeMessage"] = cause.message ?: "null"
        metadata["cause"] = cause.javaClass.toString()
        metadata["causeStackTrace"] = cause.stackTraceToString()
    }

}