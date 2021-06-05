package io.muun.apollo.data.logging

import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics
import io.muun.apollo.domain.errors.*
import io.muun.apollo.domain.utils.isInstanceOrIsCausedByError
import timber.log.Timber

class MuunTree: Timber.DebugTree() {

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

        if (LoggingContext.sendToCrashlytics && !isOnCrashlyticsBlacklist(error)) {
            sendToCrashlytics(report)
        }

        if (LoggingContext.sendToLogcat) {
            sendToLogcat(report)
        }
    }

    private fun sendFallbackCrashReport(tag: String?,
                                        message: String?,
                                        error: Throwable?,
                                        crashReportingError: Throwable) {

        if (LoggingContext.sendToLogcat) {
            Log.e("CrashReport:$tag", "During error processing", crashReportingError)
            Log.e("CrashReport:$tag", message, error)
        }

        if (LoggingContext.sendToCrashlytics) {

            val crashlytics = FirebaseCrashlytics.getInstance()

            if (error != null) {
                crashlytics.recordException(error)
            }
            crashlytics.recordException(crashReportingError)
        }
    }

    /**
     * Send the error to the system logs.
     */
    private fun sendToLogcat(report: CrashReport) {
        Log.e(report.tag, "${report.message} ${report.metadata}", report.error)
    }

    /**
     * Send the error to Crashlytics, attaching metadata as key-values with their SDK.
     */
    private fun sendToCrashlytics(report: CrashReport) {
        val crashlytics = FirebaseCrashlytics.getInstance()

        crashlytics.setCustomKey("tag", report.tag)
        crashlytics.setCustomKey("message", report.message)

        for (entry in report.metadata.entries) {
            crashlytics.setCustomKey(entry.key, entry.value.toString())
        }

        crashlytics.recordException(report.error)
    }

    /**
     * There are certain errors that are expected and/or there's nothing we can do about it (besides
     * properly informing the user about the situation), so let's try to reduce crashlytics noise by
     * silencing some common "nothing to worry about" errors.
     */
    private fun isOnCrashlyticsBlacklist(error: Throwable?): Boolean {

        // If root error has no throwable cause then there's nothing to blacklist
        // This is an ugly signature and behaviour to have but makes life easier for caller
        if (error == null) {
            return false
        }

        return when {
            error.isInstanceOrIsCausedByError<UnreachableNodeException>() -> true
            error.isInstanceOrIsCausedByError<NoPaymentRouteException>() -> true
            error.isInstanceOrIsCausedByError<InvoiceExpiredException>() -> true
            error.isInstanceOrIsCausedByError<InvoiceExpiresTooSoonException>() -> true
            error.isInstanceOrIsCausedByError<InvoiceAlreadyUsedException>() -> true
            error.isInstanceOrIsCausedByError<InvoiceMissingAmountException>() -> true
            error.isInstanceOrIsCausedByError<CyclicalSwapError>() -> true

            else -> false
        }
    }
}