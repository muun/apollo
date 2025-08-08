package io.muun.apollo.domain.model.report

import io.muun.apollo.data.logging.LoggingRequestTracker
import io.muun.apollo.data.logging.TraceErrorBuilder
import io.muun.apollo.data.logging.TraceParser
import io.muun.apollo.data.logging.TraceTransformer
import io.muun.apollo.domain.errors.ApiError
import io.muun.apollo.domain.errors.MissingCurrencyError
import io.muun.apollo.domain.errors.MuunError
import io.muun.apollo.domain.errors.WrappedErrorMessage
import io.muun.common.exception.HttpException
import java.io.PrintWriter
import java.io.Serializable
import java.io.StringWriter
import javax.money.UnknownCurrencyException

object ErrorReportBuilder {

    private val EXCLUDE_ALL = listOf(
        // Not an opinionated list. Built by observation.
        "io.muun.common.rx.ObservableFn",
        "io.muun.apollo.data.net.base.RxCallAdapterWrapper",
        "io.muun.apollo.data.net.base.-$\$Lambda\$RxCallAdapterWrapper",
        "io.muun.apollo.data.os.execution.ExecutionTransformerFactory",
        "io.muun.apollo.domain.action.base.AsyncAction",
        "rx.internal",
        "rx.observers",
        // In order to force Crashlytics to better group errors, we filter first line of stack
        // traces that come from Preconditions class so that, for example, not all checkNotNull
        // precondition fails get grouped together.
        // Note: this assumes that, as it normally happens, Preconditions lines in a stack trace are
        // the first line/s in the stack trace. With this, other Preconditions lines in a stack
        // trace will get filtered too (but it would be a pretty abnormal use of Preconditions).
        "io.muun.common.utils.Preconditions",
    )

    private val parser = TraceParser()
    private val transformer = TraceTransformer(EXCLUDE_ALL)
    private val builder = TraceErrorBuilder()

    /**
     * Build an ErrorReport by extracting relevant metadata and summarizing messages and traces.
     */
    fun build(origError: Throwable?): ErrorReport =
        build(null, origError?.message, origError)

    /**
     * Build an ErrorReport by extracting relevant metadata and summarizing messages and traces.
     */
    fun build(tag: String?, origMessage: String?, origError: Throwable?): ErrorReport {
        // Prepare the message:
        var message = origMessage ?: ""

        if (origError != null) {
            // TODO this may no longer be necessary after Timber 5 upgrade
            message = removeRedundantStackTrace(origMessage, origError)
        }

        // Prepare the error:
        var error = origError ?: WrappedErrorMessage(message)

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
        return ErrorReport(tag ?: "Apollo", message, error, origError, metadata)
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

    /**
     * Extract a metadata map from the error (or an empty map for unknown error classes).
     * Search up the "cause" hierarchy and attach the metadata from every MuunError we find.
     */
    private fun extractMetadata(error: Throwable?): MutableMap<String, Serializable> {
        val metadata = if (error is MuunError) error.extractMetadata() else mutableMapOf()

        if (error?.cause != null) {
            metadata.putAll(extractMetadata(error.cause))
        }

        return metadata
    }

    /** Remove the Stack trace from the message, if present */
    private fun removeRedundantStackTrace(timberMessage: String?, error: Throwable) =
        (timberMessage ?: "").split(error.javaClass.canonicalName!!, limit = 2)[0]
}
