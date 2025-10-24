package io.muun.apollo.data.afs

import android.net.TrafficStats
import javax.inject.Inject

class TrafficStatsInfoProvider {

    val androidMobileRxTraffic: Long
        get() {
            return significantTwoDigitsFloor(TrafficStats.getMobileRxBytes())
        }

    private fun significantTwoDigitsFloor(number: Long): Long {
        when {
            number < 0 -> return -1
            number < 100 -> return number
        }
        val str = number.toString()
        val prefix = str.take(2)
        val suffix = "0".repeat(str.length - 2)
        return (prefix + suffix).toLong()
    }
}