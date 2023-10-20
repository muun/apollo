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
     * Grant permission specified by @param permissionName for @param packageName e.g (app).
     */
    fun grantPermission(packageName: String, permissionName: String) {
        adb("pm grant $packageName $permissionName")
    }

    /**
     * Disable display of softkeyboard. Useful to avoid tricky softkeyboard appearances making test
     * flaky or non-deterministic (e.g difference running in different devices or OS versions).
     */
    fun disableSoftKeyboard() {
        adb("settings put secure show_ime_with_hard_keyboard 0")
    }

    /**
     * Enable display of softkeyboard. Reverting the effects of {@link #disableSoftKeyboard()}
     */
    fun enableSoftKeyboard() {
        adb("settings put secure show_ime_with_hard_keyboard 0")
    }
}