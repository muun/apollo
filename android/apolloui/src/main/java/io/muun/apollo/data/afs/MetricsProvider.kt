package io.muun.apollo.data.afs


import android.app.ApplicationExitInfo
import io.muun.apollo.data.net.NetworkInfoProvider
import io.muun.apollo.data.os.OS
import io.muun.apollo.domain.model.BackgroundEvent
import io.muun.apollo.domain.model.InstallSourceInfo
import io.muun.apollo.domain.model.SystemUserInfo
import io.muun.common.Optional
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class MetricsProvider @Inject constructor(
    private var activityManagerInfoProvider: ActivityManagerInfoProvider,
    private val telephonyInfoProvider: TelephonyInfoProvider,
    private val hardwareCapabilitiesProvider: HardwareCapabilitiesProvider,
    private val packageManagerInfoProvider: PackageManagerInfoProvider,
    private val cpuInfoProvider: CpuInfoProvider,
    private val buildInfoProvider: BuildInfoProvider,
    private val fileInfoProvider: FileInfoProvider,
    private val systemCapabilitiesProvider: SystemCapabilitiesProvider,
    private val appInfoProvider: AppInfoProvider,
    private val connectivityInfoProvider: ConnectivityInfoProvider,
    private val resourcesInfoProvider: ResourcesInfoProvider,
    private val dateTimeZoneProvider: DateTimeZoneProvider,
    private val localeInfoProvider: LocaleInfoProvider,
    private val trafficStatsInfoProvider: TrafficStatsInfoProvider,
    private val nfcProvider: NfcProvider,
    private val batteryInfoProvider: BatteryInfoProvider,
    private val systemInfoProvider: SystemInfoProvider,
    private val networkInfoProvider: NetworkInfoProvider,
) {
    val appImportance: Int
        get() = activityManagerInfoProvider.appImportance

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

    val simOperatorId: String
        get() = telephonyInfoProvider.simOperatorId

    val mobileNetworkId: String
        get() = telephonyInfoProvider.mobileNetworkId

    val mobileRoaming: Boolean
        get() = telephonyInfoProvider.mobileRoaming

    val mobileDataStatus: Int
        get() = telephonyInfoProvider.mobileDataStatus

    val mobileRadioType: Int
        get() = telephonyInfoProvider.mobileRadioType

    val mobileDataActivity: Int
        get() = telephonyInfoProvider.mobileDataActivity

    val telephonyNetworkRegionList: List<String>
        get() = telephonyInfoProvider.regionList

    val androidId: String
        get() = hardwareCapabilitiesProvider.androidId

    val systemUsersInfo: List<SystemUserInfo>
        get() = hardwareCapabilitiesProvider.getSystemUsersInfo()

    val drmClientIds: Map<String, String>
        get() = hardwareCapabilitiesProvider.getDrmClientIds()

    val bootCount: Int
        get() = hardwareCapabilitiesProvider.bootCount

    val glEsVersion: String
        get() = hardwareCapabilitiesProvider.glEsVersion

    val hardwareAddresses: List<String>
        get() = hardwareCapabilitiesProvider.hardwareAddresses

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

    val cpuInfo: CpuInfo
        get() = cpuInfoProvider.getCpuInfo()

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

    val efsCreationTimeInSeconds: String
        get() = fileInfoProvider.efsCreationTimeInSeconds

    val securityEnhancedBuild: String
        get() = systemCapabilitiesProvider.securityEnhancedBuild

    val bridgeRootService: String
        get() = systemCapabilitiesProvider.bridgeRootService

    val vbMeta: String
        get() = systemCapabilitiesProvider.vbMeta

    val deviceRegion: Map<String, String>?
        get() = systemCapabilitiesProvider.deviceRegion

    val totalInternalStorageInBytes: Long
        get() = hardwareCapabilitiesProvider.totalInternalStorageInBytes

    val freeInternalStorageInBytes: Long
        get() = hardwareCapabilitiesProvider.freeInternalStorageInBytes

    val freeExternalStorageInBytes: List<Long>
        get() = hardwareCapabilitiesProvider.freeExternalStorageInBytes

    val totalExternalStorageInBytes: List<Long>
        get() = hardwareCapabilitiesProvider.totalExternalStorageInBytes

    val totalRamInBytes: Long
        get() = hardwareCapabilitiesProvider.totalRamInBytes

    val freeRamInBytes: Long
        get() = hardwareCapabilitiesProvider.freeRamInBytes

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

    val proxyHttp: String
        get() = connectivityInfoProvider.proxyHttp

    val proxyHttps: String
        get() = connectivityInfoProvider.proxyHttps

    val proxySocks: String
        get() = connectivityInfoProvider.proxySocks

    val networkLink: ConnectivityInfoProvider.NetworkLink?
        get() = connectivityInfoProvider.networkLink

    val displayMetrics: ResourcesInfoProvider.DisplayMetricsInfo
        get() = resourcesInfoProvider.displayMetrics

    val timeZoneOffsetSeconds: Long
        get() = dateTimeZoneProvider.timeZoneOffsetSeconds

    val autoDateTime: Int
        get() = dateTimeZoneProvider.autoDateTime

    val autoTimeZone: Int
        get() = dateTimeZoneProvider.autoTimeZone

    val timeZoneId: String
        get() = dateTimeZoneProvider.timeZoneId

    val calendarIdentifier: String
        get() = dateTimeZoneProvider.calendarIdentifier

    val language: String
        get() = localeInfoProvider.language

    val dateFormat: String
        get() = localeInfoProvider.dateFormat

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

    val maxBatteryLevel: Int
        get() = batteryInfoProvider.maxBatteryLevel

    val batteryHealth: String
        get() = batteryInfoProvider.batteryHealth

    val batteryDischargePrediction: Long
        get() = batteryInfoProvider.batteryDischargePrediction

    val batteryStatus: String
        get() = batteryInfoProvider.batteryStatus

    val currentTimeMillis: Long
        get() = systemInfoProvider.currentTimeMillis

    val uptimeMillis: Long
        get() = systemInfoProvider.uptimeMillis

    val elapsedRealtime: Long
        get() = systemInfoProvider.elapsedRealtime
}