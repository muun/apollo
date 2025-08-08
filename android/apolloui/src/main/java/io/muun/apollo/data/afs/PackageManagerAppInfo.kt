package io.muun.apollo.data.afs

import kotlinx.serialization.Serializable

/**
 * Structured AppInfo data.
 */
@Serializable
data class PackageManagerAppInfo(
    val name: String,
    val label: String,
    val icon: Int,
    val debuggable: Boolean,
    val persistent: Boolean,
)