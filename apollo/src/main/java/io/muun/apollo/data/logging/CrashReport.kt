package io.muun.apollo.data.logging

import java.io.Serializable


data class CrashReport(
    val tag: String,
    val message: String,
    val error: Throwable,
    val metadata: Map<String, Serializable>
)