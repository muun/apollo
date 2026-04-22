package io.muun.apollo.data.afs

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.muun.apollo.domain.action.session.IsRootedDeviceAction
import io.muun.apollo.presentation.app.ApolloApplication
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MetricsProviderTimingTest {

    companion object {
        private const val TAG = "MetricsProviderTiming"
    }

    private lateinit var metricsProvider: MetricsProvider
    private lateinit var isRootedDeviceAction: IsRootedDeviceAction

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val app = context.applicationContext as ApolloApplication
        metricsProvider = app.dataComponent.metricsProvider()
        isRootedDeviceAction = IsRootedDeviceAction(context)
    }

    private fun <T> measure(
        name: String,
        timings: MutableList<Pair<String, Long>>,
        block: () -> T,
    ): T {
        val startNanoSecs = System.nanoTime()
        val result = block()
        val elapsedMicroSecs = (System.nanoTime() - startNanoSecs) / 1_000
        timings.add(name to elapsedMicroSecs)
        return result
    }

    @Test
    fun measureAllPropertyTimings() {
        val timingsInMicroSeconds = mutableListOf<Pair<String, Long>>()

        // ActivityManagerInfoProvider
        measure("isLowRamDevice", timingsInMicroSeconds) { metricsProvider.isLowRamDevice }
        measure(
            "isBackgroundRestricted",
            timingsInMicroSeconds
        ) { metricsProvider.isBackgroundRestricted }
        measure(
            "isLowMemoryKillReportSupported",
            timingsInMicroSeconds
        ) { metricsProvider.isLowMemoryKillReportSupported }
        measure("exitReasons", timingsInMicroSeconds) { metricsProvider.exitReasons }

        // TelephonyInfoProvider
        measure("dataState", timingsInMicroSeconds) { metricsProvider.dataState }
        measure("simStates", timingsInMicroSeconds) { metricsProvider.simStates }
        measure(
            "telephonyNetworkRegion",
            timingsInMicroSeconds
        ) { metricsProvider.telephonyNetworkRegion }
        measure("simRegion", timingsInMicroSeconds) { metricsProvider.simRegion }
        measure("mobileRoaming", timingsInMicroSeconds) { metricsProvider.mobileRoaming }
        measure("mobileDataStatus", timingsInMicroSeconds) { metricsProvider.mobileDataStatus }
        measure("mobileRadioType", timingsInMicroSeconds) { metricsProvider.mobileRadioType }

        // HardwareCapabilitiesProvider
        measure("androidId", timingsInMicroSeconds) { metricsProvider.androidId }
        measure("drmClientIds", timingsInMicroSeconds) { metricsProvider.drmClientIds }
        measure("bootCount", timingsInMicroSeconds) { metricsProvider.bootCount }
        measure("glEsVersion", timingsInMicroSeconds) { metricsProvider.glEsVersion }
        measure(
            "totalInternalStorageInBytes",
            timingsInMicroSeconds
        ) { metricsProvider.totalInternalStorageInBytes }
        measure(
            "totalExternalStorageInBytes",
            timingsInMicroSeconds
        ) { metricsProvider.totalExternalStorageInBytes }
        measure("totalRamInBytes", timingsInMicroSeconds) { metricsProvider.totalRamInBytes }
        measure("initOffset", timingsInMicroSeconds) { metricsProvider.bootOffset }

        // PackageManagerInfoProvider
        measure(
            "installSourceInfo",
            timingsInMicroSeconds
        ) { metricsProvider.installSourceInfo }
        measure("appInfo", timingsInMicroSeconds) { metricsProvider.appInfo }
        measure("deviceFeatures", timingsInMicroSeconds) { metricsProvider.deviceFeatures }
        measure("signatureHash", timingsInMicroSeconds) { metricsProvider.signatureHash }
        measure(
            "firstInstallTimeInMs",
            timingsInMicroSeconds
        ) { metricsProvider.firstInstallTimeInMs }
        measure("applicationId", timingsInMicroSeconds) { metricsProvider.applicationId }

        // BuildInfoProvider
        measure("buildInfo", timingsInMicroSeconds) { metricsProvider.buildInfo }
        measure("deviceName", timingsInMicroSeconds) { metricsProvider.deviceName }
        measure("deviceModel", timingsInMicroSeconds) { metricsProvider.deviceModel }

        // FileInfoProvider
        measure("quickEmProps", timingsInMicroSeconds) { metricsProvider.quickEmProps }
        measure("emArchitecture", timingsInMicroSeconds) { metricsProvider.emArchitecture }
        measure("appSize", timingsInMicroSeconds) { metricsProvider.appSize }
        measure("initId", timingsInMicroSeconds) { metricsProvider.bootId }
        measure("defaultFsDate", timingsInMicroSeconds) { metricsProvider.defaultFsDate }
        measure("androidFsDate", timingsInMicroSeconds) { metricsProvider.androidFsDate }
        measure(
            "hasUniqueBaseDateInExternalStorage",
            timingsInMicroSeconds
        ) { metricsProvider.hasUniqueBaseDateInExternalStorage }
        measure(
            "externalStorageMinDate",
            timingsInMicroSeconds
        ) { metricsProvider.externalStorageMinDate }
        measure(
            "hasNewEntriesInAppExternalStorage",
            timingsInMicroSeconds
        ) { metricsProvider.hasNewEntriesInAppExternalStorage }

        // SystemCapabilitiesProvider
        measure(
            "securityEnhancedBuild",
            timingsInMicroSeconds
        ) { metricsProvider.securityEnhancedBuild }
        measure(
            "bridgeRootService",
            timingsInMicroSeconds
        ) { metricsProvider.bridgeRootService }
        measure("vbMeta", timingsInMicroSeconds) { metricsProvider.vbMeta }
        measure("usbConnected", timingsInMicroSeconds) { metricsProvider.usbConnected }
        measure(
            "usbPersistConfig",
            timingsInMicroSeconds
        ) { metricsProvider.usbPersistConfig }
        measure("bridgeEnabled", timingsInMicroSeconds) { metricsProvider.bridgeEnabled }
        measure(
            "bridgeDaemonStatus",
            timingsInMicroSeconds
        ) { metricsProvider.bridgeDaemonStatus }
        measure(
            "developerEnabled",
            timingsInMicroSeconds
        ) { metricsProvider.developerEnabled }
        measure("internalLevel", timingsInMicroSeconds) { metricsProvider.internalLevel }

        // AppInfoProvider
        measure("appDatadir", timingsInMicroSeconds) { metricsProvider.appDatadir }
        measure(
            "latestBackgroundTimes",
            timingsInMicroSeconds
        ) { metricsProvider.latestBackgroundTimes }

        // ConnectivityInfoProvider
        measure(
            "currentNetworkTransport",
            timingsInMicroSeconds
        ) { metricsProvider.currentNetworkTransport }
        measure("vpnState", timingsInMicroSeconds) { metricsProvider.vpnState }
        measure("proxyHttpType", timingsInMicroSeconds) { metricsProvider.proxyHttpType }
        measure("proxyHttpsType", timingsInMicroSeconds) { metricsProvider.proxyHttpsType }
        measure("proxySocksType", timingsInMicroSeconds) { metricsProvider.proxySocksType }
        measure("networkLink", timingsInMicroSeconds) { metricsProvider.networkLink }

        // DateTimeZoneProvider
        measure(
            "timeZoneOffsetSeconds",
            timingsInMicroSeconds
        ) { metricsProvider.timeZoneOffsetSeconds }
        measure("autoDateTime", timingsInMicroSeconds) { metricsProvider.autoDateTime }
        measure("autoTimeZone", timingsInMicroSeconds) { metricsProvider.autoTimeZone }
        measure("timeZoneId", timingsInMicroSeconds) { metricsProvider.timeZoneId }

        // LocaleInfoProvider
        measure("language", timingsInMicroSeconds) { metricsProvider.language }
        measure("regionCode", timingsInMicroSeconds) { metricsProvider.regionCode }

        // TrafficStatsInfoProvider
        measure(
            "androidMobileRxTraffic",
            timingsInMicroSeconds
        ) { metricsProvider.androidMobileRxTraffic }

        // NfcProvider
        measure("hasNfcFeature", timingsInMicroSeconds) { metricsProvider.hasNfcFeature }
        measure("hasNfcAdapter", timingsInMicroSeconds) { metricsProvider.hasNfcAdapter }
        measure("isNfcEnabled", timingsInMicroSeconds) { metricsProvider.isNfcEnabled }
        measure(
            "nfcAntennaPosition",
            timingsInMicroSeconds
        ) { metricsProvider.nfcAntennaPosition }
        measure("deviceSizeInMm", timingsInMicroSeconds) { metricsProvider.deviceSizeInMm }
        measure(
            "isDeviceFoldable",
            timingsInMicroSeconds
        ) { metricsProvider.isDeviceFoldable }

        // BatteryInfoProvider
        measure("batteryLevel", timingsInMicroSeconds) { metricsProvider.batteryLevel }
        measure("batteryStatus", timingsInMicroSeconds) { metricsProvider.batteryStatus }
        measure(
            "batteryRemainState",
            timingsInMicroSeconds
        ) { metricsProvider.batteryRemainState }
        measure("isCharging", timingsInMicroSeconds) { metricsProvider.isCharging }

        // SystemInfoProvider
        measure(
            "currentTimeMillis",
            timingsInMicroSeconds
        ) { metricsProvider.currentTimeMillis }
        measure("uptimeMillis", timingsInMicroSeconds) { metricsProvider.uptimeMillis }
        measure("elapsedRealtime", timingsInMicroSeconds) { metricsProvider.elapsedRealtime }

        // RootHint
        measure("RootHint", timingsInMicroSeconds) { isRootedDeviceAction.isRooted() }

        // Print summary sorted by elapsed time (slowest first)
        val totalUs = timingsInMicroSeconds.sumOf { it.second }

        Log.d(TAG, "=".repeat(60))
        Log.d(TAG, "MetricsProvider property timing (sorted slowest first)")
        Log.d(TAG, "=".repeat(60))

        timingsInMicroSeconds.sortedByDescending { it.second }.forEach { (name, us) ->
            val ms = us / 1_000.0
            Log.d(TAG, "%-40s %8d μs  (%6.2f ms)".format(name, us, ms))
        }

        Log.d(TAG, "-".repeat(60))
        Log.d(TAG, "%-40s %8d μs  (%6.2f ms)".format("TOTAL", totalUs, totalUs / 1_000.0))
        Log.d(TAG, "=".repeat(60))
    }
}
