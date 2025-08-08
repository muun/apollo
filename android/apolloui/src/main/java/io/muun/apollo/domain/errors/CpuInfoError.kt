package io.muun.apollo.domain.errors

open class CpuInfoError(tag: String, cause: Throwable) : MuunError(
    "Error reading cpu info",
    cause
) {

    init {
        metadata["tag"] = tag
    }
}
