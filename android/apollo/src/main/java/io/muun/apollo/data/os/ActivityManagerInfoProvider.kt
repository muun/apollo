package io.muun.apollo.data.os

import android.app.ActivityManager
import javax.inject.Inject


class ActivityManagerInfoProvider @Inject constructor() {

    val appImportance: Int
        get() {
            val appProcessInfo = ActivityManager.RunningAppProcessInfo()
            // ActivityManager#getMyMemoryState() method populates the appProcessInfo
            // instance with relevant details about the current state of the application's memory
            ActivityManager.getMyMemoryState(appProcessInfo)
            return appProcessInfo.importance
        }
}