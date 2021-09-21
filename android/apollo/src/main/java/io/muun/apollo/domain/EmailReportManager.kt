package io.muun.apollo.domain

import android.content.Context
import io.muun.apollo.data.os.GooglePlayServicesHelper
import io.muun.apollo.data.os.TelephonyInfoProvider
import io.muun.apollo.domain.action.fcm.GetFcmTokenAction
import io.muun.apollo.domain.model.User
import io.muun.apollo.domain.model.report.CrashReport
import io.muun.apollo.domain.model.report.EmailReport
import io.muun.apollo.domain.selector.UserSelector
import io.muun.apollo.domain.utils.locale
import io.muun.common.Optional
import io.muun.common.utils.Encodings
import io.muun.common.utils.Hashes
import javax.inject.Inject

class EmailReportManager @Inject constructor(
    private val userSel: UserSelector,
    private val getFcmToken: GetFcmTokenAction,
    private val googlePlayServicesHelper: GooglePlayServicesHelper,
    private val telephonyInfoProvider: TelephonyInfoProvider,
    private val context: Context
) {

    fun buildEmailReport(report: CrashReport, presenter: String, rootHint: Boolean): EmailReport {

        val supportId = userSel.getOptional()
            .map { obj: User -> obj.supportId }
            .orElse(Optional.empty())
            .orElse(null)

        val fcmTokenHash: String = try {
            val fcmToken: String = getFcmToken.actionNow()
            Encodings.bytesToHex(Hashes.sha256(Encodings.stringToBytes(fcmToken)))
        } catch (e: Exception) {
            // GetFcmTokenAction already logs the error
            "unavailable"
        }

        val googlePlayServicesAvailable =
            googlePlayServicesHelper.isAvailable == GooglePlayServicesHelper.AVAILABLE

        return EmailReport.Builder()
            .report(report)
            .supportId(supportId)
            .fcmTokenHash(fcmTokenHash)
            .presenterName(presenter)
            .googlePlayServices(googlePlayServicesAvailable)
            .defaultRegion(telephonyInfoProvider.region.orElse("null"))
            .rootHint(rootHint)
            .locale(context.locale())
            .build()
    }
}