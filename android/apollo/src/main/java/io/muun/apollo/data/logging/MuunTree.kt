package io.muun.apollo.data.logging

import android.util.Log
import io.muun.apollo.domain.model.report.CrashReportBuilder
import timber.log.Timber

class MuunTree : Timber.DebugTree() {

    /**
     * Log a message, taking steps to enrich and report errors.
     */
    override fun log(priority: Int, tag: String?, message: String?, error: Throwable?) {
        // For non-error logs, we don't have any special treatment:
        if (priority < Log.ERROR) {
            super.log(priority, tag, message, error)
            return
        }

        sendCrashReport(tag, message, error)
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

        if (LoggingContext.sendToLogcat) {
            Log.e(report.tag, "${report.message} ${report.metadata}", report.error)
        }
    }

    private fun sendFallbackCrashReport(
        tag: String?,
        message: String?,
        error: Throwable?,
        crashReportingError: Throwable,
    ) {

        if (LoggingContext.sendToLogcat) {
            Log.e("CrashReport:$tag", "During error processing", crashReportingError)
            Log.e("CrashReport:$tag", message, error)
        }

        if (LoggingContext.sendToCrashlytics) {
            Crashlytics.reportReportingError(tag, message, error, crashReportingError)
        }
    }
}