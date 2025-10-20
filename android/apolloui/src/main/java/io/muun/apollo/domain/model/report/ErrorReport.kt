package io.muun.apollo.domain.model.report

import android.util.Log
import java.io.Serializable
import java.util.UUID
import kotlin.math.min

/**
 * This acts as a safe-guard when sending errors in our own email reports.
 * Too many data can cause crashes (e.g TransactionTooLargeException in when trying use emailIntent
 * for email reports).
 */
private const val STACK_TRACE_LIMIT_FOR_EMAIL_REPORTS = 10_000

data class ErrorReport(
    val tag: String,
    val message: String,
    val error: Throwable,
    val originalError: Throwable?,
    val metadata: MutableMap<String, Serializable>,
) {

    val uniqueId = UUID.randomUUID().toString()

    fun print(abridged: Boolean): String {
        val error = printError(abridged)
        val metadata = printMetadata()
        return "Id:${uniqueId}\nTag:$tag\nMessage:$message\nError:$error\nMetadata:\n\n$metadata"
    }

    fun printMetadata(): String {
        val builder = StringBuilder("{\n")
        for (key in metadata.keys) {
            builder.append("\t$key=${metadata[key]}\n")
        }
        builder.append("}\n")
        return builder.toString()
    }

    fun printError(abridged: Boolean = false): String {
        val stackTraceString = Log.getStackTraceString(error)
        return if (abridged) {
            val stackTraceCapSizeInChars =
                min(STACK_TRACE_LIMIT_FOR_EMAIL_REPORTS, stackTraceString.length)
            return "${stackTraceString.substring(0, stackTraceCapSizeInChars)}\nEXCEEDED MAX LENGTH"
        } else {
            stackTraceString
        }
    }

    fun getTrackingTitle(): String =
        error.javaClass.simpleName + ":" + error.localizedMessage?.replace("\n", " ")
}