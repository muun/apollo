package io.muun.apollo.domain.model.report

import android.app.ApplicationExitInfo
import android.os.Build
import io.muun.apollo.data.external.Globals
import io.muun.apollo.domain.utils.getUnsupportedCurrencies
import io.muun.common.utils.Dates
import io.muun.common.utils.Encodings
import io.muun.common.utils.Hashes
import org.threeten.bp.Instant
import org.threeten.bp.ZoneOffset
import org.threeten.bp.ZonedDateTime
import timber.log.Timber
import java.util.Locale
import javax.annotation.CheckReturnValue

class EmailReport private constructor(val body: String) {

    data class Builder(
        var report: ErrorReport? = null,
        var supportId: String? = null,
        var bigQueryPseudoId: String? = null,
        var fcmTokenHash: String? = null,
        var presenterName: String? = null,
        var googlePlayServicesAvailable: Boolean? = null,
        var googlePlayServicesVersionCode: Long? = null,
        var googlePlayServicesVersionName: String? = null,
        var googlePlayServicesClientVersionCode: Int? = null,
        var googlePlayVersionCode: Long? = null,
        var googlePlayVersionName: String? = null,
        var defaultRegion: String? = null,
        var rootHint: Boolean? = null,
        var locale: Locale? = null,
        var isLowRamDevice: Boolean? = null,
        var isBackgroundRestricted: Boolean? = null,
        var isLowMemoryKillReportSupported: Boolean? = null,
        var exitReasons: List<ApplicationExitInfo>? = null,
        var deviceName: String? = null,
        var deviceModel: String? = null,
        var deviceManufacturer: String? = null,
    ) {

        @CheckReturnValue
        fun report(report: ErrorReport) = apply { this.report = report }

        @CheckReturnValue
        fun supportId(supportId: String?) = apply { this.supportId = supportId }

        @CheckReturnValue
        fun bigQueryPseudoId(pseudoId: String?) = apply { this.bigQueryPseudoId = pseudoId }

        @CheckReturnValue
        fun fcmTokenHash(fcmTokenHash: String) = apply { this.fcmTokenHash = fcmTokenHash }

        @CheckReturnValue
        fun presenterName(presenterName: String) = apply { this.presenterName = presenterName }

        @CheckReturnValue
        fun defaultRegion(defaultRegion: String) = apply { this.defaultRegion = defaultRegion }

        @CheckReturnValue
        fun googlePlayServices(available: Boolean) = apply {
            this.googlePlayServicesAvailable = available
        }

        @CheckReturnValue
        fun googlePlayServicesVersionCode(versionCode: Long) = apply {
            this.googlePlayServicesVersionCode = versionCode
        }

        @CheckReturnValue
        fun googlePlayServicesVersionName(versionName: String) = apply {
            this.googlePlayServicesVersionName = versionName
        }

        @CheckReturnValue
        fun googlePlayServicesClientVersionCode(clientVersionCode: Int) = apply {
            this.googlePlayServicesClientVersionCode = clientVersionCode
        }

        @CheckReturnValue
        fun googlePlayVersionCode(versionCode: Long) = apply {
            this.googlePlayVersionCode = versionCode
        }

        @CheckReturnValue
        fun googlePlayVersionName(versionName: String) = apply {
            this.googlePlayVersionName = versionName
        }

        @CheckReturnValue
        fun rootHint(rootHint: Boolean) = apply { this.rootHint = rootHint }

        @CheckReturnValue
        fun locale(locale: Locale) = apply { this.locale = locale }

        @CheckReturnValue
        fun isLowRamDevice(isLowRamDevice: Boolean) = apply { this.isLowRamDevice = isLowRamDevice }

        @CheckReturnValue
        fun isBackgroundRestricted(isBackgroundRestricted: Boolean) = apply {
            this.isBackgroundRestricted = isBackgroundRestricted
        }

        @CheckReturnValue
        fun isLowMemoryKillReportSupported(isLowMemoryKillReportSupported: Boolean) = apply {
            this.isLowMemoryKillReportSupported = isLowMemoryKillReportSupported
        }

        @CheckReturnValue
        fun exitReasons(exitReasons: List<ApplicationExitInfo>) = apply {
            this.exitReasons = exitReasons
        }

        fun deviceName(deviceName: String) = apply {
            this.deviceName = deviceName
        }

        fun deviceModel(deviceModel: String) = apply {
            this.deviceModel = deviceModel
        }

        fun deviceManufacturer(deviceManufacturer: String) = apply {
            this.deviceManufacturer = deviceManufacturer
        }

        @CheckReturnValue
        fun build(abridged: Boolean): EmailReport {

            checkNotNull(report)
            checkNotNull(fcmTokenHash)
            checkNotNull(presenterName)
            checkNotNull(googlePlayServicesAvailable)
            checkNotNull(googlePlayServicesVersionCode)
            checkNotNull(googlePlayServicesVersionName)
            checkNotNull(googlePlayServicesClientVersionCode)
            checkNotNull(googlePlayVersionCode)
            checkNotNull(googlePlayVersionName)
            checkNotNull(rootHint)
            checkNotNull(defaultRegion)
            checkNotNull(locale)
            checkNotNull(isLowRamDevice)
            checkNotNull(isBackgroundRestricted)
            checkNotNull(isLowMemoryKillReportSupported)
            checkNotNull(exitReasons)
            checkNotNull(deviceName)
            checkNotNull(deviceModel)
            checkNotNull(deviceManufacturer)

            val instant = Instant.ofEpochMilli(System.currentTimeMillis())
            val now = ZonedDateTime.ofInstant(instant, ZoneOffset.UTC)

            val body =
                """|Android version: ${Build.VERSION.SDK_INT}
                   |App version: ${Globals.INSTANCE.versionName}(${Globals.INSTANCE.versionCode})
                   |Date: ${now.format(Dates.ISO_DATE_TIME_WITH_MILLIS)}
                   |Locale: ${locale.toString()}
                   |SupportId: ${if (supportId != null) supportId else "Not logged in"}
                   |Bid: $bigQueryPseudoId
                   |ScreenPresenter: $presenterName
                   |FcmTokenHash: $fcmTokenHash
                   |GooglePlayServices (GPS): $googlePlayServicesAvailable
                   |GPS System Version: $googlePlayServicesVersionCode
                   |GPS System Version Name: $googlePlayServicesVersionName
                   |GPS Client Version: $googlePlayServicesClientVersionCode
                   |GooglePlay Version: $googlePlayVersionCode
                   |GooglePlay Version Name: $googlePlayVersionName
                   |Device: $deviceName
                   |DeviceModel: $deviceModel
                   |DeviceManufacturer: $deviceManufacturer
                   |Rooted (just a hint, no guarantees): $rootHint
                   |Unsupported Currencies: ${getUnsupportedCurrencies(report!!).contentToString()}
                   |Default Region: $defaultRegion
                   |Low Ram Device: $isLowRamDevice
                   |Background Restricted: $isBackgroundRestricted
                   |Low Memory Kill Report Supported: $isLowMemoryKillReportSupported
                   |Recent Exit reasons: ${formatExitReasons(exitReasons!!)}
                   |${report!!.print(abridged)}""".trimMargin()

            Timber.d("EmailReport: \n$body")
            return EmailReport(body)
        }

        private fun formatExitReasons(exitReasons: List<ApplicationExitInfo>): String {
            val builder = StringBuilder()
            builder.append(" {\n")

            for (exitReason in exitReasons) {
                builder.append("\t$exitReason\n")
            }

            builder.append("}\n")
            return builder.toString()
        }
    }

    private fun reportId() =
        Encodings.bytesToHex(Hashes.sha256(Encodings.stringToBytes(body)))

    fun subject(subjectPrefix: String) =
        String.format("%s (#%s)", subjectPrefix, reportId().substring(0, 8))
}