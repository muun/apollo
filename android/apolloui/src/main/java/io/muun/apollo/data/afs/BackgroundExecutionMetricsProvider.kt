package io.muun.apollo.data.afs

import io.muun.apollo.domain.model.BackgroundEvent
import kotlinx.serialization.Serializable
import javax.inject.Inject

class BackgroundExecutionMetricsProvider @Inject constructor(
    private val metricsProvider: MetricsProvider
) {

    fun run(): BackgroundExecutionMetrics =
        BackgroundExecutionMetrics(
            metricsProvider.currentTimeMillis,
            metricsProvider.batteryLevel,
            metricsProvider.batteryStatus,
            metricsProvider.totalInternalStorageInBytes,
            metricsProvider.totalExternalStorageInBytes.toTypedArray(),
            metricsProvider.totalRamInBytes,
            metricsProvider.dataState,
            metricsProvider.simStates.toTypedArray(),
            metricsProvider.currentNetworkTransport,
            metricsProvider.uptimeMillis,
            metricsProvider.elapsedRealtime,
            metricsProvider.bootCount,
            metricsProvider.language,
            metricsProvider.timeZoneOffsetSeconds,
            metricsProvider.telephonyNetworkRegion.orElse(""),
            metricsProvider.simRegion,
            metricsProvider.appDatadir,
            metricsProvider.vpnState,
            metricsProvider.usbConnected,
            metricsProvider.usbPersistConfig,
            metricsProvider.bridgeEnabled,
            metricsProvider.bridgeDaemonStatus,
            metricsProvider.developerEnabled,
            metricsProvider.proxyHttp,
            metricsProvider.proxyHttps,
            metricsProvider.proxySocks,
            metricsProvider.autoDateTime,
            metricsProvider.autoTimeZone,
            metricsProvider.timeZoneId,
            metricsProvider.regionCode,
            metricsProvider.androidMobileRxTraffic,
            metricsProvider.mobileRoaming,
            metricsProvider.mobileDataStatus,
            metricsProvider.mobileRadioType,
            metricsProvider.networkLink,
            metricsProvider.hasNfcFeature,
            metricsProvider.hasNfcAdapter,
            metricsProvider.isNfcEnabled,
            metricsProvider.nfcAntennaPosition.map { "${it.first};${it.second}" }.toTypedArray(),
            metricsProvider.deviceSizeInMm?.let { "${it.first};${it.second}" } ?: "",
            metricsProvider.isDeviceFoldable,
            metricsProvider.isBackgroundRestricted,
            metricsProvider.latestBackgroundTimes,
            metricsProvider.internalLevel.let { "${it.first};${it.second}" },
            metricsProvider.batteryRemainState,
            metricsProvider.isCharging
        )

    @Suppress("ArrayInDataClass")
    @Serializable
    data class BackgroundExecutionMetrics(
        private val epochInMilliseconds: Long,
        private val batteryLevel: Int,
        private val batteryState: String,
        private val totalInternalStorage: Long,
        private val totalExternalStorage: Array<Long>,
        private val totalRamStorage: Long,
        private val dataState: String,
        private val simStates: Array<String>,
        private val networkTransport: String,
        private val androidUptimeMillis: Long,
        private val androidElapsedRealtimeMillis: Long,
        private val androidBootCount: Int,
        private val language: String,
        private val timeZoneOffsetInSeconds: Long,
        private val telephonyNetworkRegion: String,
        private val simRegion: String,
        private val appDataDir: String,
        private val vpnState: Int,
        private val usbConnected: Int,
        private val usbPersistConfig: String,
        private val bridgeEnabled: Int,
        private val bridgeDaemonStatus: String,
        private val developerEnabled: Int,
        private val proxyHttp: String,
        private val proxyHttps: String,
        private val proxySocks: String,
        private val autoDateTime: Int,
        private val autoTimeZone: Int,
        private val timeZoneId: String,
        private val regionCode: String,
        private val androidMobileRxTraffic: Long,
        private val androidMobileRoaming: Boolean,
        private val androidMobileDataStatus: Int,
        private val androidMobileRadioType: Int,
        private val androidNetworkLink: ConnectivityInfoProvider.NetworkLink?,
        private val androidHasNfcFeature: Boolean,
        private val androidHasNfcAdapter: Boolean,
        private val androidNfcEnabled: Boolean,
        private val androidNfcAntennaPositions: Array<String>, // in mms starting bottom-left
        private val androidDeviceSizeInMms: String,
        private val androidFoldableDevice: Boolean?,
        private val isBackgroundRestricted: Boolean,
        private val bkgTimes: List<BackgroundEvent>,
        private val internalLevel: String,
        private val batteryRemainState: String,
        private val isCharging: Boolean?
    )
}