package io.muun.apollo.utils

/**
 * Utility object to run with general system-wise commands (e.g clear app data,
 * send push notifications, trigger app update, etc...)
 */
object SystemCommand {

    /**
     * Clear data for specific app.
     */
    fun clearData(packageName: String) {
        adb("pm clear $packageName")
    }

    /**
     * Grant permission specified by @param permissionName for @param packageName e.g (app) .
     */
    fun grantPermission(packageName: String, permissionName: String) {
        adb("pm grant $packageName $permissionName")
    }

}