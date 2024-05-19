package io.muun.apollo.data.logging

import android.util.Log
import io.muun.apollo.domain.model.report.CrashReportBuilder
import timber.log.Timber

class MuunTree : Timber.DebugTree() {

    /**
     * Log a message, taking steps to enrich and report errors.
     */
    override fun log(priority: Int, tag: String?, message: String?, error: Throwable?) {
        // For low priority logs, we don't have any special treatment:
        if (priority < Log.INFO) {
            sendToLogcat(priority, tag, message, error)
            return
        }

        when (priority) {
            Log.INFO -> {
                sendToLogcat(Log.INFO, "Breadcrumb", message!!, null)
                @Suppress("DEPRECATION") // I know. These are the only allowed usages.
                Crashlytics.logBreadcrumb(message)
            }
            Log.WARN -> {
                sendToLogcat(Log.WARN, tag, message!!, null)
                @Suppress("DEPRECATION") // I know. These are the only allowed usages.
                Crashlytics.logBreadcrumb("Warning: $message")
            }
            else -> { // Log.ERROR && Log.ASSERT
                sendCrashReport(tag, message, error)
            }
        }
    }

    private fun sendCrashReport(tag: String?, message: String?, error: Throwable?) {
        try {
            sendPreparedCrashReport(tag, message, error)

        } catch (crashReportingError: Throwable) {
            sendFallbackCrashReport(tag, message, error, crashReportingError)
        }
    }

    private fun sendPreparedCrashReport(tag: String?, message: String?, error: Throwable?) {
        val report = CrashReportBuilder.build(tag, message, error)

        if (LoggingContext.sendToCrashlytics) {
            Crashlytics.reportError(report)
        }

        sendToLogcat(Log.ERROR, report.tag, "${report.message} ${report.metadata}", report.error)
    }

    private fun sendFallbackCrashReport(
        tag: String?,
        message: String?,
        error: Throwable?,
        crashReportingError: Throwable,
    ) {

        sendToLogcat(Log.ERROR, "CrashReport:$tag", "During error processing", crashReportingError)
        sendToLogcat(Log.ERROR, "CrashReport:$tag", message, error)

        if (LoggingContext.sendToCrashlytics) {
            Crashlytics.reportReportingError(tag, message, error, crashReportingError)
        }
    }

    private fun sendToLogcat(priority: Int, tag: String?, message: String?, error: Throwable?) {
        if (LoggingContext.sendToLogcat) {
            super.log(priority, tag, message, error)
        }
    }
}