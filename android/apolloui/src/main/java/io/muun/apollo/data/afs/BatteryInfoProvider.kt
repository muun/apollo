package io.muun.apollo.data.afs

import android.content.Context
import android.content.Context.POWER_SERVICE
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.PowerManager
import io.muun.apollo.data.os.OS

private const val UNSUPPORTED = -1
private const val UNKNOWN = -2

class BatteryInfoProvider(private val context: Context) {

    private val powerManager: PowerManager by lazy {
        context.getSystemService(POWER_SERVICE) as PowerManager
    }

    /**
     * Returns the device battery health, which will be a string constant representing the general
     * health of this device. Note: Android docs do not explain what these values exactly mean.
     */
    val batteryHealth: String
        get() = getBatteryHealthText(getBatteryProperty(BatteryManager.EXTRA_HEALTH))

    /**
     * Returns the device battery status, which will be a string constant with one of the following
     * values:
     * UNPLUGGED:   The device isn’t plugged into power; the battery is discharging.
     * CHARGING:    The device is plugged into power and the battery is less than 100% charged.
     * FULL:        The device is plugged into power and the battery is 100% charged.
     * UNKNOWN:     The battery status for the device can’t be determined.
     * UNREADABLE:  The battery status was unreadable/unrecognizable.
     */
    val batteryStatus: String
        get() = getBatteryStatusText(getBatteryProperty(BatteryManager.EXTRA_STATUS))

    /**
     * (Android only and Android 12+ only) Returns the current battery life remaining estimate,
     * expressed in nanoseconds. Will be UNKNOWN (-2) if the device is powered, charging, or an error
     * was encountered. For pre Android 12 devices it will be UNSUPPORTED (-1).
     */
    val batteryDischargePrediction: Long
        get() = if (OS.supportsBatteryDischargePrediction()) {
            powerManager.batteryDischargePrediction?.toNanos() ?: UNKNOWN.toLong()
        } else {
            UNSUPPORTED.toLong()
        }

    /**
     * Returns the current battery level, an integer from 0 to EXTRA_SCALE/MaxBatteryLevel.
     */
    val batteryLevel: Int
        get() = getBatteryProperty(BatteryManager.EXTRA_LEVEL)

    /**
     * Returns an integer representing the maximum battery level.
     */
    val maxBatteryLevel: Int
        get() = getBatteryProperty(BatteryManager.EXTRA_SCALE)

    private fun getBatteryIntent(): Intent? =
        IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            .let { intentFilter ->
                context.registerReceiver(null, intentFilter)
            }

    private fun getBatteryProperty(propertyName: String) =
        getBatteryIntent()?.getIntExtra(propertyName, -1) ?: -1

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
     * values. Note that Android docs don't really explain what these values mean.
     */
    private fun getBatteryStatusText(batteryStatus: Int): String {
        return when (batteryStatus) {
            BatteryManager.BATTERY_STATUS_FULL -> "FULL"
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "NOT_CHARGING"
            BatteryManager.BATTERY_STATUS_DISCHARGING -> "UNPLUGGED"
            BatteryManager.BATTERY_STATUS_CHARGING -> "CHARGING"
            BatteryManager.BATTERY_STATUS_UNKNOWN -> "UNKNOWN"
            else -> "UNREADABLE"
        }
    }
}