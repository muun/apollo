package io.muun.apollo.data.afs

import kotlinx.serialization.Serializable

@Serializable
data class BuildInfo(
    val abis: List<String>,
    val fingerprint: String,
    val hardware: String,
    val bootloader: String,
    val manufacturer: String,
    val brand: String,
    val display: String,
    val time: Long,
    val host: String,
    val type: String,
    val radioVersion: String?,
    val securityPatch: String,
    val baseOs: String,
)
