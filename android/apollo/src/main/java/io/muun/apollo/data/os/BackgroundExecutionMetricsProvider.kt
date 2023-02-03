package io.muun.apollo.data.os

import android.content.Context
import android.content.Context.POWER_SERVICE
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.PowerManager
import io.muun.apollo.data.net.NetworkInfoProvider
import kotlinx.serialization.Serializable
import javax.inject.Inject

class BackgroundExecutionMetricsProvider @Inject constructor(
    private val context: Context,
    private val hardwareCapabilitiesProvider: HardwareCapabilitiesProvider,
    private val telephonyInfoProvider: TelephonyInfoProvider,
    private val networkInfoProvider: NetworkInfoProvider,
) {

    private val powerManager: PowerManager by lazy {
        context.getSystemService(POWER_SERVICE) as PowerManager
    }

    fun run(): BackgroundExecutionMetrics =
        BackgroundExecutionMetrics(
            System.currentTimeMillis(),
            getBatteryLevel(),
            getMaxBatteryLevel(),
            getBatteryHealth(),
            getBatteryDischargePrediction(),
            getBatteryStatus(),
            hardwareCapabilitiesProvider.getFreeInternalStorageInBytes(),
            hardwareCapabilitiesProvider.getFreeExternalStorageInBytes().toTypedArray(),
            hardwareCapabilitiesProvider.getTotalExternalStorageInBytes().toTypedArray(),
            hardwareCapabilitiesProvider.getFreeRamInBytes(),
            telephonyInfoProvider.dataState,
            telephonyInfoProvider.getSimStates().toTypedArray(),
            networkInfoProvider.currentTransport
        )

    @Suppress("ArrayInDataClass")
    @Serializable
    data class BackgroundExecutionMetrics(
        private val epochInMilliseconds: Long,
        private val batteryLevel: Int,
        private val maxBatteryLevel: Int,
        private val batteryHealth: String,
        private val batteryDischargePrediction: Long?,
        private val batteryState: String,
        private val freeInternalStorage: Long,
        private val freeExternalStorage: Array<Long>,
        private val totalExternalStorage: Array<Long>,
        private val freeRamStorage: Long,
        private val dataState: String,
        private val simStates: Array<String>,
        private val networkTransport: String,
    )

    /**
     * Returns the device battery health, which will be a string constant representing the general
     * health of this device. Note: Android docs do not explain what these values exactly mean.
     */
    private fun getBatteryHealth(): String =
        getBatteryHealthText(getBatteryProperty(BatteryManager.EXTRA_HEALTH))

    /**
     * Returns the device battery status, which will be a string constant with one of the following
     * values:
     * UNPLUGGED:   The device isn’t plugged into power; the battery is discharging.
     * CHARGING:    The device is plugged into power and the battery is less than 100% charged.
     * FULL:        The device is plugged into power and the battery is 100% charged.
     * UNKNOWN:     The battery status for the device can’t be determined.
     * UNREADABLE:  The battery status was unreadable/unrecognizable.
     */
    private fun getBatteryStatus(): String =
        getBatteryStatusText(getBatteryProperty(BatteryManager.EXTRA_STATUS))

    /**
     * (Android only and Android 12+ only) Returns the current battery life remaining estimate,
     * expressed in nanoseconds. Will be null if the device is powered, charging, or an error
     * was encountered. For pre Android 12 devices it will be -1.
     */
    private fun getBatteryDischargePrediction(): Long? =
        if (OS.supportsBatteryDischargePrediction()) {
            powerManager.batteryDischargePrediction?.toNanos()
        } else {
            -1
        }

    private fun getBatteryIntent(): Intent? =
        IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            .let { intentFilter ->
                context.registerReceiver(null, intentFilter)
            }

    private fun getBatteryProperty(propertyName: String) =
        getBatteryIntent()?.getIntExtra(propertyName, -1) ?: -1

    /**
     * Returns the current battery level, an integer from 0 to EXTRA_SCALE/MaxBatteryLevel.
     */
    private fun getBatteryLevel() =
        getBatteryProperty(BatteryManager.EXTRA_LEVEL)

    /**
     * Returns an integer representing the maximum battery level.
     */
    private fun getMaxBatteryLevel(): Int =
        getBatteryProperty(BatteryManager.EXTRA_SCALE)

    private fun getBatteryHealthText(batteryHealth: Int): String {
        return when (batteryHealth) {
            BatteryManager.BATTERY_HEALTH_COLD -> "COLD"
            BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "UNSPECIFIED_FAILURE"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "OVER_VOLTAGE"
            BatteryManager.BATTERY_HEALTH_DEAD -> "DEAD"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "OVERHEAT"
            BatteryManager.BATTERY_HEALTH_GOOD -> "GOOD"
            BatteryManager.BATTERY_HEALTH_UNKNOWN -> "UNKNOWN"
            else -> "UNREADABLE"
        }
    }

    /**
     * Translate Android's BatteryManager battery status int constants into one of our domain
     * values. Note that Android docs don't really explain what these values mean. Also, there may
     * be some confusion regarding BATTERY_STATUS_NOT_CHARGING and BATTERY_STATUS_CHARGING.
     * According to some reports:
     * - https://stackoverflow.com/questions/7404185/battery-is-low-charging-current-not-enough-is-there-intent-before-this-messag.
     * - https://stackoverflow.com/questions/10022960/android-difference-between-battery-status-discharging-and-battery-status-not-c
     * one or both of these values could be returned when the device is actually plugged in but
     * not receiving enough power to actually charge. For our intent and purpose this case will
     * be treated as UNPLUGGED.
     */
    private fun getBatteryStatusText(batteryStatus: Int): String {
        return when (batteryStatus) {
            BatteryManager.BATTERY_STATUS_FULL -> "FULL"
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "UNPLUGGED"
            BatteryManager.BATTERY_STATUS_DISCHARGING -> "UNPLUGGED"
            BatteryManager.BATTERY_STATUS_CHARGING -> "CHARGING"
            BatteryManager.BATTERY_STATUS_UNKNOWN -> "UNKNOWN"
            else -> "UNREADABLE"
        }
    }
}