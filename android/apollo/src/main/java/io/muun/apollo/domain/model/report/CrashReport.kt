package io.muun.apollo.domain.model.report

import android.util.Log
import java.io.Serializable

data class CrashReport(
    val tag: String,
    val message: String,
    val error: Throwable,
    val originalError: Throwable?,
    val metadata: MutableMap<String, Serializable>,
) {

    fun print() =
        "Tag:$tag\nMessage:$message\nError:${printError()}\nMetadata:{\n\n${printMetadata()}}"

    fun printMetadata(): String {
        val builder = StringBuilder()
        for (key in metadata.keys) {
            builder.append("$key=${metadata[key]}\n\n")
        }
        return builder.toString()
    }

    fun printError(): String =
        Log.getStackTraceString(error)

    fun getTrackingTitle(): String =
        error.javaClass.simpleName + ":" + error.localizedMessage?.replace("\n", " ")
}