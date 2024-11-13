package io.muun.apollo.data.net

import android.net.TrafficStats
import javax.inject.Inject

class TrafficStatsInfoProvider @Inject constructor() {

    val androidMobileRxTraffic: Long
        get() {
            return TrafficStats.getMobileRxBytes()
        }
}