package io.muun.apollo.domain.model.report

import android.os.Build
import io.muun.apollo.data.external.Globals
import io.muun.apollo.domain.utils.getUnsupportedCurrencies
import io.muun.common.utils.Encodings
import io.muun.common.utils.Hashes

class EmailReport private constructor(val body: String) {

    data class Builder(
        var report: CrashReport? = null,
        var supportId: String? = null,
        var fcmTokenHash: String? = null,
        var presenterName: String? = null,
        var googlePlayServicesAvailable: Boolean? = null,
        var defaultRegion: String? = null,
        var rootHint: Boolean? = null,
    ) {

        fun report(report: CrashReport) = apply { this.report = report }
        fun supportId(supportId: String?) = apply { this.supportId = supportId }
        fun fcmTokenHash(fcmTokenHash: String) = apply { this.fcmTokenHash = fcmTokenHash }
        fun presenterName(presenterName: String) = apply { this.presenterName = presenterName }
        fun defaultRegion(defaultRegion: String) = apply { this.defaultRegion = defaultRegion }
        fun googlePlayServices(available: Boolean) = apply {
            this.googlePlayServicesAvailable = available
        }
        fun rootHint(rootHint: Boolean) = apply { this.rootHint = rootHint }

        fun build(): EmailReport {

            checkNotNull(report)
            checkNotNull(fcmTokenHash)
            checkNotNull(presenterName)
            checkNotNull(googlePlayServicesAvailable)
            checkNotNull(rootHint)
            checkNotNull(defaultRegion)

            val body =
                """|Android version: ${Build.VERSION.SDK_INT}
                   |App version: ${Globals.INSTANCE.versionName}(${Globals.INSTANCE.versionCode})
                   |SupportId: ${if (supportId != null) "Not logged in" else supportId}
                   |ScreenPresenter: $presenterName
                   |FcmTokenHash: $fcmTokenHash
                   |GooglePlayServices: $googlePlayServicesAvailable
                   |Device: ${Globals.INSTANCE.deviceName}
                   |DeviceModel: ${Globals.INSTANCE.deviceModel}
                   |DeviceManufacturer: ${Globals.INSTANCE.deviceManufacturer}
                   |Rooted (just a hint, no guarantees): $rootHint
                   |Unsupported Currencies: ${getUnsupportedCurrencies(report!!).contentToString()}
                   |Default Region: $defaultRegion
                   |${report!!.print()}""".trimMargin()

            return EmailReport(body)
        }
    }

    private fun reportId() =
        Encodings.bytesToHex(Hashes.sha256(Encodings.stringToBytes(body)))

    fun subject(subjectPrefix: String) =
        String.format("%s (#%s)", subjectPrefix, reportId().substring(0, 8))
}