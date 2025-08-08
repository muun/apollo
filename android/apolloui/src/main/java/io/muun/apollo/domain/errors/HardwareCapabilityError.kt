package io.muun.apollo.domain.errors

open class HardwareCapabilityError(capability: String, cause: Throwable) : MuunError(
    "Error reading hardware capability",
    cause
) {

    init {
        metadata["capability"] = capability
    }
}
