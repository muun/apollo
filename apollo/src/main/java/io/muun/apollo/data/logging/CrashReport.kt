package io.muun.apollo.data.logging

import android.util.Log
import java.io.Serializable


data class CrashReport(
    val tag: String,
    val message: String,
    val error: Throwable,
    val metadata: MutableMap<String, Serializable>
) {

    fun print() =
        "Tag:$tag\nMessage:$message\nError:${Log.getStackTraceString(error)}\nMetadata:$metadata"
}