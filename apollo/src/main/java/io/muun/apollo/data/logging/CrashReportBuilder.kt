package io.muun.apollo.data.logging

import io.muun.apollo.domain.errors.ApiError
import io.muun.apollo.domain.errors.MissingCurrencyError
import io.muun.apollo.domain.errors.MuunError
import io.muun.apollo.domain.errors.WrappedErrorMessage
import io.muun.common.exception.HttpException
import java.io.PrintWriter
import java.io.StringWriter
import javax.money.UnknownCurrencyException

object CrashReportBuilder {

    private val INCLUDE_ANY = listOf("io.muun")

    private val EXCLUDE_ALL = listOf(
        // Not an opinionated list. Built by observation.
        "io.muun.common.rx.ObservableFn",
        "io.muun.apollo.data.net.base.RxCallAdapterWrapper",
        "io.muun.apollo.data.net.base.-$\$Lambda\$RxCallAdapterWrapper",
        "io.muun.apollo.data.os.execution.ExecutionTransformerFactory",
        "io.muun.apollo.domain.action.base.AsyncAction"
    )

    private val parser = TraceParser()
    private val transformer = TraceTransformer(INCLUDE_ANY, EXCLUDE_ALL)
    private val builder = TraceErrorBuilder()

    /**
     * Build a CrashReport by extracting relevant metadata and summarizing messages and traces.
     */
    fun build(origError: Throwable?): CrashReport =
        build(null, origError?.message, origError)

    /**
     * Build a CrashReport by extracting relevant metadata and summarizing messages and traces.
     */
    fun build(tag: String?, origMessage: String?, origError: Throwable?): CrashReport {
        // Prepare the message:
        var message = origMessage ?: ""

        if (origError != null) {
            message = removeRedundantStackTrace(origMessage, origError) ?: ""
        }

        // Prepare the error:
        var error = origError ?: WrappedErrorMessage(message)

        if (error.stackTrace == null) {
            error.fillInStackTrace()
        }

        error = when (error) {
            is HttpException -> ApiError(error)
            is UnknownCurrencyException -> MissingCurrencyError(error)
            else -> error
        }

        // Prepare the metadata: (needs to happen BEFORE summarization bc we reassign the variable)
        val metadata = extractMetadata(error)
        metadata["recentRequests"] = LoggingRequestTracker.getRecentRequests().toString()

        error = summarize(error)

        // Done!
        return CrashReport(tag ?: "Apollo", message, error, metadata)
    }

    /** Craft a summarized Throwable */
    private fun summarize(e: Throwable) =
        captureStackTrace(e)
            .let(parser::parse)
            .let(transformer::transform)
            .let(builder::build)

    /** Obtain the complete stack trace as a String. */
    private fun captureStackTrace(error: Throwable) =
        StringWriter().apply { error.printStackTrace(PrintWriter(this)) }.toString()

    /** Extract a metadata map from the error (or an empty map for unknown error classes). */
    private fun extractMetadata(error: Throwable?) =
        if (error is MuunError) error.metadata.toMutableMap() else mutableMapOf()

    /** Remove the Stack trace from the message, if present */
    private fun removeRedundantStackTrace(timberMessage: String?, error: Throwable) =
        (timberMessage ?: "").split(error.javaClass.canonicalName, limit=2)[0]
}
