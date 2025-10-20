package io.muun.apollo.data.logging

import android.util.Log
import io.muun.apollo.domain.model.report.ErrorReportBuilder
import timber.log.Timber

class MuunTree : Timber.DebugTree() {

    /**
     * Log a message, taking steps to enrich and report errors.
     * Automatically infers the tag from the calling class (See Timber.DebugTree).
     */
    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        // For low priority logs, we don't have any special treatment:
        if (priority < Log.INFO) {
            sendToLogcat(priority, tag, message, t)
            return
        }

        when (priority) {
            Log.INFO -> {
                sendToLogcat(Log.INFO, tag, "Breadcrumb: $message", null)
                @Suppress("DEPRECATION") // I know. These are the only allowed usages.
                Crashlytics.logBreadcrumb(message)
            }

            Log.WARN -> {
                sendToLogcat(Log.WARN, tag, message, null)
                @Suppress("DEPRECATION") // I know. These are the only allowed usages.
                Crashlytics.logBreadcrumb("Warning: $message")
            }

            else -> { // Log.ERROR && Log.ASSERT
                sendErrorReport(tag, message, t)
            }
        }
    }

    private fun sendErrorReport(tag: String?, message: String, error: Throwable?) {
        try {
            sendPreparedErrorReport(tag, message, error)

        } catch (reportingError: Throwable) {
            sendFallbackErrorReport(tag, message, error, reportingError)
        }
    }

    private fun sendPreparedErrorReport(tag: String?, message: String, error: Throwable?) {
        val report = ErrorReportBuilder.build(tag, message, error)

        if (LoggingContext.sendToCrashlytics) {
            Crashlytics.reportError(report)
        }

        // Report.message gets the stacktrace removed so we need original message
        sendToLogcat(Log.ERROR, tag, "$message\n${report.printMetadata()}", report.error)
    }

    private fun sendFallbackErrorReport(
        tag: String?,
        message: String,
        error: Throwable?,
        reportingError: Throwable,
    ) {

        sendToLogcat(Log.ERROR, tag, "During error processing", reportingError)
        sendToLogcat(Log.ERROR, tag, message, error)

        if (LoggingContext.sendToCrashlytics) {
            Crashlytics.reportReportingError(tag, message, error, reportingError)
        }
    }

    private fun sendToLogcat(priority: Int, tag: String?, message: String, error: Throwable?) {
        // No matter what env or kind or build this is, we want to log errors ;)
        if (LoggingContext.sendToLogcat || priority >= Log.ERROR) {
            // Named params are required to avoid calling new method overload :s
            // log(priority: Int, message: String?, vararg args: Any?)
            // Also, error is totally ignored by this call :s. It assumes a previous behavior by
            // timber.log.Timber.Tree.prepareLog() where message is appended
            // getStackTraceString(error).
            super.log(priority = priority, tag = tag, message = message, t = error)
        }
    }
}