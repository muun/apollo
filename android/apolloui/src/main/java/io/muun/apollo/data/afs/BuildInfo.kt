package io.muun.apollo.data.afs

import kotlinx.serialization.Serializable

@Serializable
data class BuildInfo(
    val abis: List<String>,
    val bootloader: String,
    val manufacturer: String,
    val brand: String,
    val host: String,
    val type: String,
    val radioVersion: String?,
    val securityPatch: String,
    // These fields are null on older app versions
    val model: String?,
    val product: String?,
    val release: String?
)
