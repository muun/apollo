package io.muun.apollo.domain

import android.content.Context
import io.muun.apollo.data.os.GooglePlayHelper
import io.muun.apollo.data.os.GooglePlayServicesHelper
import io.muun.apollo.data.os.TelephonyInfoProvider
import io.muun.apollo.data.preferences.FirebaseInstallationIdRepository
import io.muun.apollo.domain.action.session.IsRootedDeviceAction
import io.muun.apollo.domain.model.report.CrashReport
import io.muun.apollo.domain.model.report.EmailReport
import io.muun.apollo.domain.model.user.User
import io.muun.apollo.domain.selector.UserSelector
import io.muun.apollo.domain.utils.locale
import io.muun.common.utils.Encodings
import io.muun.common.utils.Hashes
import timber.log.Timber
import javax.inject.Inject

class EmailReportManager @Inject constructor(
    private val userSel: UserSelector,
    private val googlePlayServicesHelper: GooglePlayServicesHelper,
    private val googlePlayHelper: GooglePlayHelper,
    private val telephonyInfoProvider: TelephonyInfoProvider,
    private val isRootedDeviceAction: IsRootedDeviceAction,
    private val firebaseInstallationIdRepo: FirebaseInstallationIdRepository,
    private val context: Context,
) {

    fun buildAbridgedEmailReport(report: CrashReport, presenterName: String): EmailReport {
        return buildEmailReport(report, presenterName, abridged = true)
    }

    fun buildEmailReport(
        report: CrashReport,
        presenterName: String,
        abridged: Boolean = false,
    ): EmailReport {

        val supportId = userSel.getOptional()
            .flatMap { obj: User -> obj.supportId }
            .orElse(null)

        return EmailReport.Builder()
            .report(report)
            .supportId(supportId)
            .bigQueryPseudoId(firebaseInstallationIdRepo.getBigQueryPseudoId())
            .fcmTokenHash(getFcmTokenHash())
            .presenterName(presenterName)
            .googlePlayServices(googlePlayServicesHelper.isAvailable)
            .googlePlayServicesVersionCode(googlePlayServicesHelper.versionCode)
            .googlePlayServicesVersionName(googlePlayServicesHelper.versionName)
            .googlePlayServicesClientVersionCode(googlePlayServicesHelper.clientVersionCode)
            .googlePlayVersionCode(googlePlayHelper.versionCode)
            .googlePlayVersionName(googlePlayHelper.versionName)
            .defaultRegion(telephonyInfoProvider.region.orElse("null"))
            .rootHint(isRootedDeviceAction.actionNow())
            .locale(context.locale())
            .build(abridged)
    }

    private fun getFcmTokenHash() = try {
        val fcmToken: String? = firebaseInstallationIdRepo.getFcmToken()

        if (fcmToken != null) {
            Encodings.bytesToHex(Hashes.sha256(Encodings.stringToBytes(fcmToken)))

        } else {
            "null"
        }
    } catch (e: Throwable) {  // Avoid crash, we're already processing an error (report).
        Timber.e(e)
        "unavailable"
    }
}