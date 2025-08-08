package io.muun.apollo.data.afs

import android.net.TrafficStats
import javax.inject.Inject

class TrafficStatsInfoProvider {

    val androidMobileRxTraffic: Long
        get() {
            return TrafficStats.getMobileRxBytes()
        }
}