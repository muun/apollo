package io.muun.apollo.data.afs

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.PowerManager
import io.muun.apollo.data.os.OS

class BatteryInfoProvider(private val context: Context) {

    private val powerManager: PowerManager by lazy {
        context.getSystemService(Context.POWER_SERVICE) as PowerManager
    }

    private val batteryManager: BatteryManager by lazy {
        context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
    }

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

    val isCharging: Boolean?
        get() = if (OS.supportsBatteryManagerIsCharging()) {
            batteryManager.isCharging
        } else {
            null
        }

    val batteryRemainState: String
        get() {
            if (!OS.supportsBatteryDischargePrediction()) {
                return Constants.UNKNOWN
            }
            val prediction = powerManager.batteryDischargePrediction?.toNanos()
            if (prediction == null) {
                return "CHARGING"
            }

            return when {
                prediction < 0 -> "NEGATIVE"
                prediction.toInt() == 0 -> "ZERO"
                else -> "POSITIVE"
            }
        }

    /**
     * Returns the current battery level, an integer from 0 to EXTRA_SCALE/MaxBatteryLevel.
     */
    val batteryLevel: Int
        get() = getBatteryProperty(BatteryManager.EXTRA_LEVEL)

    private fun getBatteryIntent(): Intent? =
        IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            .let { intentFilter ->
                context.registerReceiver(null, intentFilter)
            }

    private fun getBatteryProperty(propertyName: String) =
        getBatteryIntent()?.getIntExtra(propertyName, -1) ?: -1

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