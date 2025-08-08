package io.muun.apollo.data.afs

import android.os.SystemClock

class SystemInfoProvider {
    val currentTimeMillis: Long
        get() = System.currentTimeMillis()

    val uptimeMillis: Long
        get() = SystemClock.uptimeMillis()

    val elapsedRealtime: Long
        get() = SystemClock.elapsedRealtime()
}