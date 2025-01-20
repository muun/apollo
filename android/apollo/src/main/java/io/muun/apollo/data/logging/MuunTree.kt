package io.muun.apollo.data.logging

import android.util.Log
import io.muun.apollo.domain.model.report.CrashReportBuilder
import timber.log.Timber

class MuunTree : Timber.DebugTree() {

    /**
     * Log a message, taking steps to enrich and report errors.
     * Automatically infers the tag from the calling class (See Timber.DebugTree).
     */
    override fun log(priority: Int, error: Throwable?, message: String?, vararg args: Any?) {
        // For low priority logs, we don't have any special treatment:
        if (priority < Log.INFO) {
            sendToLogcat(priority, message, error)
            return
        }

        when (priority) {
            Log.INFO -> {
                sendToLogcat(Log.INFO, "Breadcrumb: ${message!!}", null)
                @Suppress("DEPRECATION") // I know. These are the only allowed usages.
                Crashlytics.logBreadcrumb(message)
            }
            Log.WARN -> {
                sendToLogcat(Log.WARN, message!!, null)
                @Suppress("DEPRECATION") // I know. These are the only allowed usages.
                Crashlytics.logBreadcrumb("Warning: $message")
            }
            else -> { // Log.ERROR && Log.ASSERT
                sendCrashReport(message, error)
            }
        }
    }

    private fun sendCrashReport(message: String?, error: Throwable?) {
        try {
            sendPreparedCrashReport(message, error)

        } catch (crashReportingError: Throwable) {
            sendFallbackCrashReport(message, error, crashReportingError)
        }
    }

    private fun sendPreparedCrashReport(message: String?, error: Throwable?) {
        val report = CrashReportBuilder.build(message, error)

        if (LoggingContext.sendToCrashlytics) {
            Crashlytics.reportError(report)
        }

        sendToLogcat(Log.ERROR, "${report.message} ${report.metadata}", report.error)
    }

    private fun sendFallbackCrashReport(
        message: String?,
        error: Throwable?,
        crashReportingError: Throwable,
    ) {

        sendToLogcat(Log.ERROR, "During error processing", crashReportingError)
        sendToLogcat(Log.ERROR, message, error)

        if (LoggingContext.sendToCrashlytics) {
            Crashlytics.reportReportingError(message, error, crashReportingError)
        }
    }

    private fun sendToLogcat(priority: Int, message: String?, error: Throwable?) {
        if (LoggingContext.sendToLogcat) {
            super.log(priority, message, error)
        }
    }
}