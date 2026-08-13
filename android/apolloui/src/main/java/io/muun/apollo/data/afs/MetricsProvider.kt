package io.muun.apollo.data.afs


import android.app.ApplicationExitInfo
import io.muun.apollo.data.net.NetworkInfoProvider
import io.muun.apollo.data.os.OS
import io.muun.apollo.domain.action.session.IsRootedDeviceAction
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
    private val isRootedDeviceAction: IsRootedDeviceAction,
    private val runtimeInfoProvider: RuntimeInfoProvider,
) {

    val isRootHint: Boolean by lazy { isRootedDeviceAction.isRooted() }

    val isLowRamDevice: Boolean by lazy { activityManagerInfoProvider.isLowRamDevice }

    val isBackgroundRestricted: Boolean
        get() = activityManagerInfoProvider.isBackgroundRestricted

    val isLowMemoryKillReportSupported: Boolean by lazy {
        activityManagerInfoProvider.isLowMemoryKillReportSupported
    }

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

    val androidId: String by lazy { hardwareCapabilitiesProvider.androidId }

    val drmClientIds: Map<String, String> by lazy { hardwareCapabilitiesProvider.getDrmClientIds() }

    val bootCount: Int by lazy { hardwareCapabilitiesProvider.bootCountDiscrete }

    val glEsVersion: String by lazy { hardwareCapabilitiesProvider.glEsVersion }

    val installSourceInfo: InstallSourceInfo by lazy {
        packageManagerInfoProvider.installSourceInfo
    }

    val appInfo: PackageManagerAppInfo by lazy { packageManagerInfoProvider.appInfo }

    val deviceFeatures: PackageManagerDeviceFeatures by lazy {
        packageManagerInfoProvider.deviceFeatures
    }

    val signatureHash: String by lazy { packageManagerInfoProvider.signatureHash }

    val firstInstallTimeInMs: Long by lazy { packageManagerInfoProvider.firstInstallTimeInMs }

    val buildInfo: BuildInfo by lazy { buildInfoProvider.buildInfo }

    val deviceName: String by lazy { buildInfoProvider.deviceName }

    val deviceModel: String by lazy { buildInfoProvider.deviceModel }

    val quickEmProps: Int by lazy { fileInfoProvider.quickEmProps }

    val emArchitecture: Int by lazy { fileInfoProvider.emArchitecture }

    val appSize: Long by lazy { fileInfoProvider.appSize }

    val securityEnhancedBuild: String by lazy { systemCapabilitiesProvider.securityEnhancedBuild }

    val bridgeRootService: String
        get() = systemCapabilitiesProvider.bridgeRootService

    val vbMeta: String by lazy { systemCapabilitiesProvider.vbMeta }

    val totalInternalStorageInBytes: Long
        get() = hardwareCapabilitiesProvider.totalInternalStorageInBytes

    val totalExternalStorageInBytes: List<Long>
        get() = hardwareCapabilitiesProvider.totalExternalStorageInBytes

    val totalRamInBytes: Long by lazy { hardwareCapabilitiesProvider.totalRamInBytes }

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

    val appDatadir: String by lazy { appInfoProvider.appDatadir }

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

    val hasNfcFeature: Boolean by lazy { nfcProvider.hasNfcFeature }

    val hasNfcAdapter: Boolean by lazy { nfcProvider.hasNfcAdapter }

    val isNfcEnabled: Boolean
        get() = nfcProvider.isNfcEnabled

    val nfcAntennaPosition: List<Pair<Float, Float>> by lazy { nfcProvider.nfcAntennaPosition }

    val deviceSizeInMm: Pair<Int, Int>? by lazy { nfcProvider.deviceSizeInMm }

    val isDeviceFoldable: Boolean? by lazy { nfcProvider.isDeviceFoldable }

    val hasNfcHostCardEmulation: Int by lazy { nfcProvider.hasNfcHostCardEmulation }

    val hasNfcOffHostCardEmulationUicc: Int by lazy {
        nfcProvider.hasNfcOffHostCardEmulationUicc
    }

    val hasNfcOffHostCardEmulationEse: Int by lazy {
        nfcProvider.hasNfcOffHostCardEmulationEse
    }

    val nfcExtendedApduSupportedEmpirical: Int
        get() = nfcProvider.nfcExtendedApduSupportedEmpirical

    val nfcMaxTransceiveLengthEmpirical: Int
        get() = nfcProvider.nfcMaxTransceiveLengthEmpirical

    val nfcConfigFilesPresent: List<String> by lazy { nfcProvider.nfcConfigFilesPresent }

    val nfcChipIdentifier: String by lazy { nfcProvider.nfcChipIdentifier }

    val nfcConfigFileHash: String by lazy { nfcProvider.nfcConfigFileHash }

    val nfcExtendedApduSupportedReflected: Int by lazy {
        nfcProvider.nfcExtendedApduSupportedReflected
    }

    val nfcMaxTransceiveLengthReflected: Int by lazy {
        nfcProvider.nfcMaxTransceiveLengthReflected
    }

    val nfcReflectionFailureReason: String by lazy { nfcProvider.nfcReflectionFailureReason }

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

    val internalLevel: Pair<Int, Int> by lazy { systemCapabilitiesProvider.internalLevel }

    val applicationId: String by lazy { packageManagerInfoProvider.applicationId }

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

    val bootOffset: Int by lazy { hardwareCapabilitiesProvider.bootOffset }

    val bootId: String by lazy { fileInfoProvider.bootId }

    val extraStackElements: List<String>
        get() = runtimeInfoProvider.extraStackElements

    val uidSharedStatus: Int by lazy { runtimeInfoProvider.uidSharedStatus }

    val widevineSecurityLevel: String by lazy { hardwareCapabilitiesProvider.widevineSecurityLevel }

    val widevineMajorVersion: Int by lazy { hardwareCapabilitiesProvider.widevineMajorVersion }

    val runtimeExternalPackages: List<String> by lazy { runtimeInfoProvider.externalPackages }

    val appOpsPackageName: String by lazy { runtimeInfoProvider.appOpsPackageName }

    val restrictiveSdkStatus: Int by lazy { runtimeInfoProvider.restrictiveSdkStatus }

    val allSignatureHashes: List<String> by lazy { packageManagerInfoProvider.allSignatureHashes }

    val archiveSignatureHashes: List<String> by lazy { packageManagerInfoProvider.archiveSignatureHashes }

    val appBasePackageName: String by lazy { runtimeInfoProvider.appBasePackageName }

    val isPlainTextDrmId: Int by lazy { hardwareCapabilitiesProvider.isPlainTextDrmId }

    val contextSwapDrmId: String by lazy { hardwareCapabilitiesProvider.contextSwapDrmId }

    val drmIdNativeHook: Int by lazy { runtimeInfoProvider.drmIdNativeHook }
}