package io.muun.apollo.domain

import io.muun.apollo.domain.model.report.CrashReport
import io.muun.apollo.domain.model.report.EmailReport
import io.muun.apollo.data.os.GooglePlayServicesHelper
import io.muun.apollo.data.os.TelephonyInfoProvider
import io.muun.apollo.domain.action.fcm.GetFcmTokenAction
import io.muun.apollo.domain.model.User
import io.muun.apollo.domain.selector.UserSelector
import io.muun.common.Optional
import io.muun.common.utils.Encodings
import io.muun.common.utils.Hashes
import javax.inject.Inject

class EmailReportManager @Inject constructor(
    private val userSel: UserSelector,
    private val getFcmToken: GetFcmTokenAction,
    private val googlePlayServicesHelper: GooglePlayServicesHelper,
    private val telephonyInfoProvider: TelephonyInfoProvider
) {

    fun buildEmailReport(report: CrashReport, presenter: String, rootHint: Boolean): EmailReport {

        val supportId = userSel.getOptional()
            .map { obj: User -> obj.supportId }
            .orElse(Optional.empty())
            .orElse(null)

        val fcmToken = getFcmToken.actionNow() // Insta-return, token ready at this point

        val fcmTokenHash = if (fcmToken != null) {
            Encodings.bytesToHex(Hashes.sha256(Encodings.stringToBytes(fcmToken)))
        } else {
            "null"
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
            .build()
    }
}