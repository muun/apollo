package io.muun.apollo.domain

import android.content.Context
import io.muun.apollo.data.os.GooglePlayHelper
import io.muun.apollo.data.os.GooglePlayServicesHelper
import io.muun.apollo.data.os.TelephonyInfoProvider
import io.muun.apollo.domain.action.fcm.GetFcmTokenAction
import io.muun.apollo.domain.action.session.IsRootedDeviceAction
import io.muun.apollo.domain.model.report.CrashReport
import io.muun.apollo.domain.model.report.EmailReport
import io.muun.apollo.domain.model.user.User
import io.muun.apollo.domain.selector.UserSelector
import io.muun.apollo.domain.utils.locale
import io.muun.common.utils.Encodings
import io.muun.common.utils.Hashes
import javax.inject.Inject

class EmailReportManager @Inject constructor(
    private val userSel: UserSelector,
    private val getFcmToken: GetFcmTokenAction,
    private val googlePlayServicesHelper: GooglePlayServicesHelper,
    private val googlePlayHelper: GooglePlayHelper,
    private val telephonyInfoProvider: TelephonyInfoProvider,
    private val isRootedDeviceAction: IsRootedDeviceAction,
    private val context: Context,
) {

    fun buildEmailReport(report: CrashReport, presenterName: String): EmailReport {

        val supportId = userSel.getOptional()
            .flatMap { obj: User -> obj.supportId }
            .orElse(null)

        val fcmTokenHash: String = try {
            val fcmToken: String = getFcmToken.actionNow()
            Encodings.bytesToHex(Hashes.sha256(Encodings.stringToBytes(fcmToken)))
        } catch (e: Throwable) {  // Avoid crash, we're already processing an error (report).
            // GetFcmTokenAction already logs the error
            "unavailable"
        }

        val googlePlayServicesAvailable =
            googlePlayServicesHelper.isAvailable == GooglePlayServicesHelper.AVAILABLE

        return EmailReport.Builder()
            .report(report)
            .supportId(supportId)
            .fcmTokenHash(fcmTokenHash)
            .presenterName(presenterName)
            .googlePlayServices(googlePlayServicesAvailable)
            .googlePlayServicesVersionCode(googlePlayServicesHelper.versionCode)
            .googlePlayServicesVersionName(googlePlayServicesHelper.versionName)
            .googlePlayServicesClientVersionCode(googlePlayServicesHelper.clientVersionCode)
            .googlePlayVersionCode(googlePlayHelper.versionCode)
            .googlePlayVersionName(googlePlayHelper.versionName)
            .defaultRegion(telephonyInfoProvider.region.orElse("null"))
            .rootHint(isRootedDeviceAction.actionNow())
            .locale(context.locale())
            .build()
    }
}