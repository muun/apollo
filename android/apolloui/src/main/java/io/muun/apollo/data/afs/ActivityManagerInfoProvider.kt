package io.muun.apollo.data.afs

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import io.muun.apollo.data.external.Globals
import io.muun.apollo.data.os.OS


class ActivityManagerInfoProvider(context: Context) {

    private val activityManager: ActivityManager =
        context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

    val appImportance: Int
        get() {
            val appProcessInfo = ActivityManager.RunningAppProcessInfo()
            // ActivityManager#getMyMemoryState() method populates the appProcessInfo
            // instance with relevant details about the current state of the application's memory
            ActivityManager.getMyMemoryState(appProcessInfo)
            return appProcessInfo.importance
        }

    /**
     * Returns true if this is a low-RAM device. Exactly whether a device is low-RAM is ultimately
     * up to the device configuration, but currently it generally means something with 1GB or less
     * of RAM. This is mostly intended to be used by apps to determine whether they should turn off
     * certain features that require more RAM.
     * AKA "is Android GO".
     */
    val isLowRamDevice: Boolean
        get() {
            return activityManager.isLowRamDevice
        }

    /**
     * Query whether the user has enabled background restrictions for this app.
     * The user may chose to do this, if they see that an app is consuming an unreasonable
     * amount of battery while in the background
     * If true, any work that the app tries to do will be aggressively restricted while it is in the
     * background. At a minimum, jobs and alarms will not execute and foreground services cannot be
     * started unless an app activity is in the foreground.
     *
     * Note that these restrictions stay in effect even when the device is charging.
     */
    val isBackgroundRestricted: Boolean
        get() {
            return if (OS.supportsIsBackgroundRestricted()) {
                activityManager.isBackgroundRestricted
            } else {
                false
            }
        }

    val isRunningInUserTestHarness: Boolean
        get() {
            return if (OS.supportsIsRunningInUserTestHarness()) {
                ActivityManager.isRunningInUserTestHarness()
            } else {
                @Suppress("DEPRECATION") // Deprecated, prefer isRunningInUserTestHarness
                ActivityManager.isRunningInTestHarness()
            }
        }

    val isUserAMonkey: Boolean
        get() {
            return ActivityManager.isUserAMonkey()
        }

    val isLowMemoryKillReportSupported: Boolean
        get() {
            return if (OS.supportsLowMemoryKillReport()) {
                ActivityManager.isLowMemoryKillReportSupported()
            } else {
                false
            }
        }

    val exitReasons: List<ApplicationExitInfo>
        get() {
            return if (OS.supportsgetHistoricalProcessExitReasons()) {
                activityManager.getHistoricalProcessExitReasons(
                    Globals.INSTANCE.applicationId,
                    0,
                    0
                )
            } else {
                emptyList()
            }
        }

}