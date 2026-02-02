package io.muun.apollo.domain.model.feebump

data class FeeBumpFunctions(
    val uuid: String,
    // Each fee bump functions is codified as a base64 string.
    val functions: List<String>
)
