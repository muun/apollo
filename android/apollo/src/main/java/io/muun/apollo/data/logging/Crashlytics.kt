package io.muun.apollo.data.logging

import com.google.firebase.crashlytics.FirebaseCrashlytics
import io.muun.apollo.domain.action.debug.ForceCrashReportAction
import io.muun.apollo.domain.errors.newop.CyclicalSwapError
import io.muun.apollo.domain.errors.fcm.FcmTokenNotAvailableError
import io.muun.apollo.domain.errors.newop.InvoiceAlreadyUsedException
import io.muun.apollo.domain.errors.newop.InvoiceExpiredException
import io.muun.apollo.domain.errors.newop.InvoiceExpiresTooSoonException
import io.muun.apollo.domain.errors.newop.InvoiceMissingAmountException
import io.muun.apollo.domain.errors.newop.NoPaymentRouteException
import io.muun.apollo.domain.errors.newop.UnreachableNodeException
import io.muun.apollo.domain.model.report.CrashReport
import io.muun.apollo.domain.utils.isInstanceOrIsCausedByError
import timber.log.Timber

object Crashlytics {

    private val crashlytics = FirebaseCrashlytics.getInstance()

    /**
     * Set up Crashlytics metadata.
     */
    @JvmStatic
    fun configure(email: String?, userId: String) {
        crashlytics.setUserId(userId)
        crashlytics.setCustomKey("email", email ?: "unknown")

        // TODO: use setUserEmail, and grab Houston session UUID to attach it
    }

    /**
     * Add custom log event, to be displayed under Logs tab. Also queryable via Bigquery. See:
     * https://firebase.google.com/docs/crashlytics/customize-crash-reports?platform=android#add-logs
     */
    @JvmStatic
    fun logBreadcrumb(breadcrumb: String) {
        Timber.d("Breadcrumb: $breadcrumb")
        crashlytics.log(breadcrumb);
    }

    /**
     * Send the error to Crashlytics, attaching metadata as key-values with their SDK.
     */
    fun reportError(report: CrashReport) {

        // Silence some common "nothing to worry about" errors
        if (isOnCrashlyticsBlacklist(report.originalError)) {
            return
        }

        crashlytics.setCustomKey("tag", report.tag)
        crashlytics.setCustomKey("message", report.message)
        crashlytics.setCustomKey("locale", LoggingContext.locale)

        for (entry in report.metadata.entries) {
            crashlytics.setCustomKey(entry.key, entry.value.toString())
        }

        crashlytics.recordException(report.error)
    }

    /**
     * Send a "fallback" reporting error to Crashlytics. This means that there was an error while
     * doing our usual error report processing. Hence we try to report the original error data (tag,
     * message, error) and the error that happened while reporting.
     */
    fun reportReportingError(
        tag: String?,
        message: String?,
        originalError: Throwable?,
        crashReportingError: Throwable,
    ) {

        tag?.let { crashlytics.setCustomKey("tag", it) }
        message?.let { crashlytics.setCustomKey("message", it) }

        if (originalError != null) {
            crashlytics.recordException(originalError)
        }

        crashlytics.recordException(crashReportingError)
    }

    /**
     * Forcefully report an exception, bypassing our usual error processing. Meant to only be used
     * by ForceCrashReportAction.
     *
     * @see ForceCrashReportAction
     */
    fun forceReport(error: ForceCrashReportAction.ForcedCrashlyticsCall) {
        crashlytics.recordException(error)
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

            error.isInstanceOrIsCausedByError<FcmTokenNotAvailableError>() -> true

            else -> false
        }
    }
}