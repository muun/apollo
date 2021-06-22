package io.muun.apollo.domain.model.report

import android.util.Log
import java.io.Serializable

data class CrashReport(
    val tag: String,
    val message: String,
    val error: Throwable,
    val metadata: MutableMap<String, Serializable>
) {

    fun print() =
        "Tag:$tag\nMessage:$message\nError:${printError()}\nMetadata:{\n\n${printMetadata()}}"

    private fun printMetadata(): String {
        val builder = StringBuilder()
        for (key in metadata.keys) {
            builder.append("$key=${metadata[key]}\n\n")
        }
        return builder.toString()
    }

    private fun printError() = Log.getStackTraceString(error)
}