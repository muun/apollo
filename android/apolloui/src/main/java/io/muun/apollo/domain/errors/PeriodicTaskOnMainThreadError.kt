package io.muun.apollo.domain.errors

class PeriodicTaskOnMainThreadError(taskName: String) : MuunError() {

    init {
        metadata["task"] = taskName
    }

}