package io.muun.apollo.data.logging

import android.util.Log
import io.muun.apollo.domain.model.report.CrashReportBuilder
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
                sendCrashReport(tag, message, t)
            }
        }
    }

    private fun sendCrashReport(tag: String?, message: String, error: Throwable?) {
        try {
            sendPreparedCrashReport(tag, message, error)

        } catch (crashReportingError: Throwable) {
            sendFallbackCrashReport(tag, message, error, crashReportingError)
        }
    }

    private fun sendPreparedCrashReport(tag: String?, message: String, error: Throwable?) {
        val report = CrashReportBuilder.build(message, error)

        if (LoggingContext.sendToCrashlytics) {
            Crashlytics.reportError(report)
        }

        sendToLogcat(Log.ERROR, tag, "${report.message} ${report.metadata}", report.error)
    }

    private fun sendFallbackCrashReport(
        tag: String?,
        message: String,
        error: Throwable?,
        crashReportingError: Throwable,
    ) {

        sendToLogcat(Log.ERROR, tag, "During error processing", crashReportingError)
        sendToLogcat(Log.ERROR, tag, message, error)

        if (LoggingContext.sendToCrashlytics) {
            Crashlytics.reportReportingError(message, error, crashReportingError)
        }
    }

    private fun sendToLogcat(priority: Int, tag: String?, message: String, error: Throwable?) {
        // No matter what env or kind or build this is, we want to log errors ;)
        if (LoggingContext.sendToLogcat || priority >= Log.ERROR) {
            // Named params are required to avoid calling new method overload :s
            // log(priority: Int, message: String?, vararg args: Any?)
            super.log(priority = priority, tag = tag, message = message, t = error)
        }
    }
}