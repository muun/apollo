package io.muun.apollo.data.afs


import android.app.ApplicationExitInfo
import io.muun.apollo.data.net.NetworkInfoProvider
import io.muun.apollo.data.os.OS
import io.muun.apollo.domain.model.BackgroundEvent
import io.muun.apollo.domain.model.InstallSourceInfo
import io.muun.common.Optional
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class MetricsProvider @Inject constructor(
    private var activityManagerInfoProvider: ActivityManagerInfoProvider,
    private val telephonyInfoProvider: TelephonyInfoProvider,
    private val hardwareCapabilitiesProvider: HardwareCapabilitiesProvider,
    private val packageManagerInfoProvider: PackageManagerInfoProvider,
    private val buildInfoProvider: BuildInfoProvider,
    private val fileInfoProvider: FileInfoProvider,
    private val systemCapabilitiesProvider: SystemCapabilitiesProvider,
    private val appInfoProvider: AppInfoProvider,
    private val connectivityInfoProvider: ConnectivityInfoProvider,
    private val dateTimeZoneProvider: DateTimeZoneProvider,
    private val localeInfoProvider: LocaleInfoProvider,
    private val trafficStatsInfoProvider: TrafficStatsInfoProvider,
    private val nfcProvider: NfcProvider,
    private val batteryInfoProvider: BatteryInfoProvider,
    private val systemInfoProvider: SystemInfoProvider,
    private val networkInfoProvider: NetworkInfoProvider,
) {
    val isLowRamDevice: Boolean
        get() = activityManagerInfoProvider.isLowRamDevice

    val isBackgroundRestricted: Boolean
        get() = activityManagerInfoProvider.isBackgroundRestricted

    val isLowMemoryKillReportSupported: Boolean
        get() = activityManagerInfoProvider.isLowMemoryKillReportSupported

    val exitReasons: List<ApplicationExitInfo>
        get() = activityManagerInfoProvider.exitReasons

    val dataState: String
        get() = telephonyInfoProvider.dataState

    val simStates: List<String>
        get() = telephonyInfoProvider.simStates

    val telephonyNetworkRegion: Optional<String>
        get() = telephonyInfoProvider.region

    val simRegion: String
        get() = telephonyInfoProvider.simRegion

    val mobileRoaming: Boolean
        get() = telephonyInfoProvider.mobileRoaming

    val mobileDataStatus: Int
        get() = telephonyInfoProvider.mobileDataStatus

    val mobileRadioType: Int
        get() = telephonyInfoProvider.mobileRadioType

    val androidId: String
        get() = hardwareCapabilitiesProvider.androidId

    val drmClientIds: Map<String, String>
        get() = hardwareCapabilitiesProvider.getDrmClientIds()

    val bootCount: Int
        get() = hardwareCapabilitiesProvider.bootCount

    val glEsVersion: String
        get() = hardwareCapabilitiesProvider.glEsVersion

    val installSourceInfo: InstallSourceInfo
        get() = packageManagerInfoProvider.installSourceInfo

    val appInfo: PackageManagerAppInfo
        get() = packageManagerInfoProvider.appInfo

    val deviceFeatures: PackageManagerDeviceFeatures
        get() = packageManagerInfoProvider.deviceFeatures

    val signatureHash: String
        get() = packageManagerInfoProvider.signatureHash

    val firstInstallTimeInMs: Long
        get() = packageManagerInfoProvider.firstInstallTimeInMs

    val buildInfo: BuildInfo
        get() = buildInfoProvider.buildInfo

    val deviceName: String
        get() = buildInfoProvider.deviceName

    val deviceModel: String
        get() = buildInfoProvider.deviceModel

    val quickEmProps: Int
        get() = fileInfoProvider.quickEmProps

    val emArchitecture: Int
        get() = fileInfoProvider.emArchitecture

    val appSize: Long
        get() = fileInfoProvider.appSize

    val securityEnhancedBuild: String
        get() = systemCapabilitiesProvider.securityEnhancedBuild

    val bridgeRootService: String
        get() = systemCapabilitiesProvider.bridgeRootService

    val vbMeta: String
        get() = systemCapabilitiesProvider.vbMeta

    val totalInternalStorageInBytes: Long
        get() = hardwareCapabilitiesProvider.totalInternalStorageInBytes

    val totalExternalStorageInBytes: List<Long>
        get() = hardwareCapabilitiesProvider.totalExternalStorageInBytes

    val totalRamInBytes: Long
        get() = hardwareCapabilitiesProvider.totalRamInBytes

    val usbConnected: Int
        get() = systemCapabilitiesProvider.usbConnected

    val usbPersistConfig: String
        get() = systemCapabilitiesProvider.usbPersistConfig

    val bridgeEnabled: Int
        get() = systemCapabilitiesProvider.bridgeEnabled

    val bridgeDaemonStatus: String
        get() = systemCapabilitiesProvider.bridgeDaemonStatus

    val developerEnabled: Int
        get() = systemCapabilitiesProvider.developerEnabled

    val appDatadir: String
        get() = appInfoProvider.appDatadir

    val latestBackgroundTimes: List<BackgroundEvent>
        get() = appInfoProvider.latestBackgroundTimes

    /**
     * While Android's NetworkInfo Class (used in networkInfoProvider to watch current network info)
     * has been deprecated, its functionality is complemented by ConnectivityManager methods
     * for newer APIs. Backward compatibility is maintained in the response values to ensure
     * consistent data handling across all Android versions
     */
    val currentNetworkTransport: String
        get() = if (OS.supportsActiveNetwork()) {
            connectivityInfoProvider.activeNetworkTransport
        } else {
            networkInfoProvider.currentTransport
        }

    val vpnState: Int
        get() = connectivityInfoProvider.vpnState

    val proxyHttpType: Int
        get() = connectivityInfoProvider.proxyHttpType

    val proxyHttpsType: Int
        get() = connectivityInfoProvider.proxyHttpsType

    val proxySocksType: Int
        get() = connectivityInfoProvider.proxySocksType

    val networkLink: ConnectivityInfoProvider.NetworkLink?
        get() = connectivityInfoProvider.networkLink

    val timeZoneOffsetSeconds: Long
        get() = dateTimeZoneProvider.timeZoneOffsetSeconds

    val autoDateTime: Int
        get() = dateTimeZoneProvider.autoDateTime

    val autoTimeZone: Int
        get() = dateTimeZoneProvider.autoTimeZone

    val timeZoneId: String
        get() = dateTimeZoneProvider.timeZoneId

    val language: String
        get() = localeInfoProvider.language

    val regionCode: String
        get() = localeInfoProvider.regionCode

    val androidMobileRxTraffic: Long
        get() = trafficStatsInfoProvider.androidMobileRxTraffic

    val hasNfcFeature: Boolean
        get() = nfcProvider.hasNfcFeature

    val hasNfcAdapter: Boolean
        get() = nfcProvider.hasNfcAdapter

    val isNfcEnabled: Boolean
        get() = nfcProvider.isNfcEnabled

    val nfcAntennaPosition: List<Pair<Float, Float>>
        get() = nfcProvider.nfcAntennaPosition

    val deviceSizeInMm: Pair<Int, Int>?
        get() = nfcProvider.deviceSizeInMm

    val isDeviceFoldable: Boolean?
        get() = nfcProvider.isDeviceFoldable

    val batteryLevel: Int
        get() = batteryInfoProvider.batteryLevel

    val batteryStatus: String
        get() = batteryInfoProvider.batteryStatus

    val batteryRemainState: String
        get() = batteryInfoProvider.batteryRemainState

    val isCharging: Boolean?
        get() = batteryInfoProvider.isCharging

    val currentTimeMillis: Long
        get() = systemInfoProvider.currentTimeMillis

    val uptimeMillis: Long
        get() = systemInfoProvider.uptimeMillis

    val elapsedRealtime: Long
        get() = systemInfoProvider.elapsedRealtime

    val internalLevel: Pair<Int, Int>
        get() = systemCapabilitiesProvider.internalLevel

    val applicationId: String
        get() = packageManagerInfoProvider.applicationId

    val defaultFsDate: Long
        get() = fileInfoProvider.defaultDate

    val androidFsDate: Long
        get() = fileInfoProvider.androidDate

    val hasUniqueBaseDateInExternalStorage: Int
        get() = fileInfoProvider.hasUniqueBaseDateInExternalStorage

    val externalStorageMinDate: Long
        get() = fileInfoProvider.externalMinDate

    val hasNewEntriesInAppExternalStorage: Int
        get() = fileInfoProvider.hasNewEntriesInAppExternalStorage
}