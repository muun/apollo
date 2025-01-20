package io.muun.apollo.data.logging

import android.app.Application
import android.os.Build
import com.google.firebase.crashlytics.FirebaseCrashlytics
import io.muun.apollo.data.analytics.AnalyticsProvider
import io.muun.apollo.data.os.ActivityManagerInfoProvider
import io.muun.apollo.data.os.GooglePlayServicesHelper
import io.muun.apollo.data.os.OS
import io.muun.apollo.data.os.TelephonyInfoProvider
import io.muun.apollo.data.os.getInstallSourceInfo
import io.muun.apollo.domain.action.debug.ForceCrashReportAction
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
import io.muun.apollo.domain.model.report.CrashReport
import io.muun.apollo.domain.utils.isInstanceOrIsCausedByError
import timber.log.Timber

object Crashlytics {

    private val crashlytics = if (LoggingContext.sendToCrashlytics) {
        FirebaseCrashlytics.getInstance()
    } else {
        null
    }

    private var analyticsProvider: AnalyticsProvider? = null
    private var activityManagerInfoProvider: ActivityManagerInfoProvider? = null

    private var bigQueryPseudoId: String? = null

    private var googlePlayServicesAvailable: Boolean? = null

    private var installSource: InstallSourceInfo? = null

    private var region: String? = null

    private var defaultUncaughtExceptionHandler: Thread.UncaughtExceptionHandler? = null

    @JvmStatic
    fun init(application: Application) {
        this.analyticsProvider = AnalyticsProvider(application)
        this.analyticsProvider?.loadBigQueryPseudoId()
            ?.subscribe({ bigQueryPseudoId = it }, { Timber.e(it) })

        this.googlePlayServicesAvailable = GooglePlayServicesHelper(application).isAvailable
        this.installSource = application.getInstallSourceInfo()
        this.region = TelephonyInfoProvider(application).region.orElse("null")

        this.defaultUncaughtExceptionHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler(customUncaughtExceptionHandler)

        this.activityManagerInfoProvider = ActivityManagerInfoProvider(application)
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
    fun reportError(report: CrashReport) {

        // Silence some common "nothing to worry about" errors
        if (isOnCrashlyticsBlacklist(report.originalError)) {
            return
        }

        // Note: these custom keys are associated with the non-fatal error being tracked but also
        // with the subsequent crash if the error generates one (e.g if error isn't caught/handled).
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

        crashlytics?.setCustomKey("isLowRamDevice", activityManagerInfoProvider!!.isLowRamDevice)
        crashlytics?.setCustomKey(
            "isBackgroundRestricted",
            activityManagerInfoProvider!!.isBackgroundRestricted
        )
        crashlytics?.setCustomKey(
            "isRunningInUserTestHarness",
            activityManagerInfoProvider!!.isRunningInUserTestHarness
        )
        crashlytics?.setCustomKey(
            "isLowMemoryKillReportSupported",
            activityManagerInfoProvider!!.isLowMemoryKillReportSupported
        )
    }

    private fun getSupportedAbi() =
        if (OS.supportsSupportedAbis()) {
            Build.SUPPORTED_ABIS[0]
        } else {
            "api19"
        }

    /**
     * Send a "fallback" reporting error to Crashlytics. This means that there was an error while
     * doing our usual error report processing. Hence we try to report the original error data (tag,
     * message, error) and the error that happened while reporting.
     */
    fun reportReportingError(
        message: String?,
        originalError: Throwable?,
        crashReportingError: Throwable,
    ) {

        message?.let { crashlytics?.setCustomKey("message", it) }

        if (originalError != null) {
            crashlytics?.recordException(originalError)
        }

        crashlytics?.recordException(crashReportingError)
    }

    /**
     * Forcefully report an exception, bypassing our usual error processing. Meant to only be used
     * by ForceCrashReportAction.
     *
     * @see ForceCrashReportAction
     */
    fun forceReport(error: ForceCrashReportAction.ForcedCrashlyticsCall) {
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