package io.muun.apollo.domain.model.report

import android.util.Log
import java.io.Serializable
import kotlin.math.min

/**
 * This acts as a safe-guard when sending errors in our own email reports.
 * Too many data can cause crashes (e.g TransactionTooLargeException in when trying use emailIntent
 * for email reports).
 */
private const val STACK_TRACE_LIMIT_FOR_EMAIL_REPORTS = 10_000
private const val STACK_TRACE_LIMIT_FOR_ANALYTICS = 500

data class ErrorReport(
    val tag: String,
    val message: String,
    val error: Throwable,
    val originalError: Throwable?,
    val metadata: MutableMap<String, Serializable>,
) {

    fun print(abridged: Boolean) =
        "Tag:$tag\nMessage:$message\nError:${printError(abridged)}\nMetadata:{\n\n${printMetadata()}}"

    fun printMetadata(): String {
        val builder = StringBuilder()
        for (key in metadata.keys) {
            builder.append("$key=${metadata[key]}\n\n")
        }
        return builder.toString()
    }

    private fun printError(abridged: Boolean = false): String {
        val stackTraceString = Log.getStackTraceString(error)
        return if (abridged) {
            val stackTraceCapSizeInChars =
                min(STACK_TRACE_LIMIT_FOR_EMAIL_REPORTS, stackTraceString.length)
            return "${stackTraceString.substring(0, stackTraceCapSizeInChars)}\nEXCEEDED MAX LENGTH"
        } else {
            stackTraceString
        }
    }

    fun printErrorForAnalytics(): String {
        val stackTraceString = Log.getStackTraceString(error)
        val stackTraceCapSizeInChars = min(STACK_TRACE_LIMIT_FOR_ANALYTICS, stackTraceString.length)
        return stackTraceString.substring(0, stackTraceCapSizeInChars)
            .replace("\n", " ")
    }

    fun getTrackingTitle(): String =
        error.javaClass.simpleName + ":" + error.localizedMessage?.replace("\n", " ")
}