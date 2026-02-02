package io.muun.apollo.data.logging

import android.app.Application
import android.os.Build
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.installations.FirebaseInstallations
import io.muun.apollo.data.afs.EarlyMetricsProvider
import io.muun.apollo.data.analytics.AnalyticsProvider
import io.muun.apollo.data.os.GooglePlayServicesHelper
import io.muun.apollo.domain.action.debug.ForceErrorReportAction
import io.muun.apollo.domain.analytics.AnalyticsEvent
import io.muun.apollo.domain.errors.fcm.FcmTokenNotAvailableError
import io.muun.apollo.domain.errors.newop.CyclicalSwapError
import io.muun.apollo.domain.errors.newop.InvoiceAlreadyUsedException
import io.muun.apollo.domain.errors.newop.InvoiceExpiredException
import io.muun.apollo.domain.errors.newop.InvoiceExpiresTooSoonException
import io.muun.apollo.domain.errors.newop.InvoiceMissingAmountException
import io.muun.apollo.domain.errors.newop.NoPaymentRouteException
import io.muun.apollo.domain.errors.newop.UnreachableNodeException
import io.muun.apollo.domain.model.InstallSourceInfo
import io.muun.apollo.domain.model.report.ErrorReport
import io.muun.apollo.domain.utils.isInstanceOrIsCausedByError
import timber.log.Timber

object Crashlytics {

    private val crashlytics = if (LoggingContext.sendToCrashlytics) {
        FirebaseCrashlytics.getInstance()
    } else {
        null
    }

    private var analyticsProvider: AnalyticsProvider? = null
    private lateinit var earlyMetricsProvider: EarlyMetricsProvider

    private var bigQueryPseudoId: String? = null

    private var googlePlayServicesAvailable: Boolean? = null

    private var installSource: InstallSourceInfo? = null

    private var region: String? = null

    private var defaultUncaughtExceptionHandler: Thread.UncaughtExceptionHandler? = null

    @JvmStatic
    fun init(application: Application) {
        this.earlyMetricsProvider = EarlyMetricsProvider(application)
        this.analyticsProvider = AnalyticsProvider(application)
        this.analyticsProvider?.loadBigQueryPseudoId()
            ?.subscribe({ bigQueryPseudoId = it }, { Timber.e(it) })

        this.googlePlayServicesAvailable = GooglePlayServicesHelper(application).isAvailable
        this.installSource = earlyMetricsProvider.installSourceInfo
        this.region = earlyMetricsProvider.region

        this.defaultUncaughtExceptionHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler(customUncaughtExceptionHandler)

        FirebaseInstallations.getInstance().id
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val fid = task.result
                    crashlytics?.setCustomKey("instanceId", fid)
                } else {
                    crashlytics?.setCustomKey("instanceId", "unavailable")
                }
            }
    }

    // enhance crashlytics crashes with custom keys
    private val customUncaughtExceptionHandler = Thread.UncaughtExceptionHandler { thread, ex ->

        setStaticCustomKeys()

        //call the default exception handler
        this.defaultUncaughtExceptionHandler?.uncaughtException(thread, ex)
    }

    /**
     * Set up Crashlytics metadata.
     */
    @JvmStatic
    fun configure(userId: String) {
        crashlytics?.setUserId(userId)
    }

    /**
     * Add custom log event, to be displayed under Logs tab. Also queryable via Bigquery. See:
     * https://firebase.google.com/docs/crashlytics/customize-crash-reports?platform=android#add-logs
     */
    @JvmStatic
    @Deprecated("Not really but you shouldn't use this directly. Use Timber.i(). See MuunTree.")
    fun logBreadcrumb(breadcrumb: String) {
        crashlytics?.log(breadcrumb)
        analyticsProvider?.report(
            AnalyticsEvent.E_BREADCRUMB(
                breadcrumb
            )
        )
    }

    /**
     * Send the error to Crashlytics, attaching metadata as key-values with their SDK.
     */
    fun reportError(report: ErrorReport) {

        // Silence some common "nothing to worry about" errors
        if (isOnCrashlyticsBlacklist(report.originalError)) {
            return
        }

        // Note: these custom keys are associated with the non-fatal error being tracked but also
        // with the subsequent crash if the error generates one (e.g if error isn't caught/handled).
        crashlytics?.setCustomKey("errorId", report.uniqueId)
        crashlytics?.setCustomKey("tag", report.tag)
        crashlytics?.setCustomKey("message", report.message)
        setStaticCustomKeys()

        for (entry in report.metadata.entries) {
            crashlytics?.setCustomKey(entry.key, entry.value.toString())
        }


        analyticsProvider?.report(
            AnalyticsEvent.E_CRASHLYTICS_ERROR(report)
        )

        crashlytics?.recordException(report.error)
    }

    private fun setStaticCustomKeys() {
        crashlytics?.setCustomKey("locale", LoggingContext.locale)
        crashlytics?.setCustomKey("region", region ?: "null")
        crashlytics?.setCustomKey("bigQueryPseudoId", bigQueryPseudoId ?: "null")
        crashlytics?.setCustomKey("abi", getSupportedAbi())
        crashlytics?.setCustomKey("isPlayServicesAvailable", googlePlayServicesAvailable.toString())
        crashlytics?.setCustomKey(
            "installSource-installingPackage",
            installSource?.installingPackageName ?: "null"
        )
        crashlytics?.setCustomKey(
            "installSource-initiatingPackage",
            installSource?.initiatingPackageName ?: "null"
        )

        crashlytics?.setCustomKey("isLowRamDevice", earlyMetricsProvider.isLowRamDevice)
        crashlytics?.setCustomKey(
            "isBackgroundRestricted",
            earlyMetricsProvider.isBackgroundRestricted
        )
        crashlytics?.setCustomKey(
            "isRunningInUserTestHarness",
            earlyMetricsProvider.isRunningInUserTestHarness
        )
        crashlytics?.setCustomKey(
            "isLowMemoryKillReportSupported",
            earlyMetricsProvider.isLowMemoryKillReportSupported
        )
    }

    private fun getSupportedAbi(): String =
        Build.SUPPORTED_ABIS[0]

    /**
     * Send a "fallback" reporting error to Crashlytics. This means that there was an error while
     * doing our usual error report processing. Hence we try to report the original error data (tag,
     * message, error) and the error that happened while reporting.
     */
    fun reportReportingError(
        tag: String?,
        message: String?,
        originalError: Throwable?,
        reportingError: Throwable,
    ) {

        tag?.let { crashlytics?.setCustomKey("tag", it) }
        message?.let { crashlytics?.setCustomKey("message", it) }

        if (originalError != null) {
            crashlytics?.recordException(originalError)
        }

        crashlytics?.recordException(reportingError)
    }

    /**
     * Forcefully report an exception, bypassing our usual error processing. Meant to only be used
     * by ForceErrorReportAction`.
     *
     * @see ForceErrorReportAction
     */
    fun forceReport(error: ForceErrorReportAction.ForcedCrashlyticsCall) {
        crashlytics?.recordException(error)
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