package io.muun.apollo.data.afs

import android.content.Context
import io.muun.apollo.domain.model.InstallSourceInfo

/**
 * Fallback class intended for use *only* when dependency injection (DI) has not yet been initialized.
 * Typically used to access internal metrics in early startup phases, before Dagger components are available.
 *
 * DO NOT use this class once DI is active.
 */

class EarlyMetricsProvider(context: Context) {

    private val telephonyInfoProvider: TelephonyInfoProvider = TelephonyInfoProvider(context)

    private val activityManagerInfoProvider: ActivityManagerInfoProvider =
        ActivityManagerInfoProvider(context)

    private val packageManagerInfoProvider: PackageManagerInfoProvider =
        PackageManagerInfoProvider(context)

    private val buildInfoProvider: BuildInfoProvider = BuildInfoProvider()


    val region: String = telephonyInfoProvider.region.orElse("null")

    val isLowRamDevice: Boolean = activityManagerInfoProvider.isLowRamDevice

    val isBackgroundRestricted: Boolean = activityManagerInfoProvider.isBackgroundRestricted

    val isRunningInUserTestHarness: Boolean = activityManagerInfoProvider.isRunningInUserTestHarness

    val isLowMemoryKillReportSupported: Boolean =
        activityManagerInfoProvider.isLowMemoryKillReportSupported

    val installSourceInfo: InstallSourceInfo =
        packageManagerInfoProvider.installSourceInfo

    val deviceName: String = buildInfoProvider.deviceName

    val sdkLevel: Int = buildInfoProvider.sdkLevel
}