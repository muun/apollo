package io.muun.apollo.utils

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObjectNotFoundException
import androidx.test.uiautomator.UiSelector
import timber.log.Timber

/**
 * Based on the SUPERB work from the guys at AdevintaSpain.
 * https://github.com/AdevintaSpain/Barista/blob/55481beb10422b094fc51f0d8f0c6653ca445fcf/barista/src/main/java/com/adevinta/android/barista/interaction/PermissionGranter.kt
 */
class PermissionGranter(
    override val device: UiDevice,
    override val context: Context,
) : WithMuunInstrumentationHelpers {

    private val PERMISSIONS_DIALOG_DELAY = 3 //Seconds

    private val PERMISSION_DIALOG_ALLOW_FOREGROUND_IDS = listOf(
        "com.android.permissioncontroller:id/permission_allow_foreground_only_button",
        "com.android.permissioncontroller:id/permission_allow_button",
        "com.android.packageinstaller:id/permission_allow_button"
    )

    private val PERMISSION_DIALOG_ALLOW_ONE_TIME_IDS = listOf(
        "com.android.permissioncontroller:id/permission_allow_one_time_button",
        "com.android.permissioncontroller:id/permission_allow_button",
        "com.android.packageinstaller:id/permission_allow_button"
    )

    // In API 30 the "Deny" button has the first ID when it is shown the first time. Second time
    // it has the second ID (do not ask again), although text is the same. In API 29 the buttons
    // are shown separately.
    private val PERMISSION_DIALOG_DENY_IDS = listOf(
        "com.android.permissioncontroller:id/permission_deny_button",
        "com.android.permissioncontroller:id/permission_deny_and_dont_ask_again_button",
        "com.android.packageinstaller:id/permission_deny_button"
    )

    private fun List<String>.toPermissionButtonRegex() = joinToString(
        prefix = "^(",
        separator = "|",
        postfix = ")$"
    ) { it }

    fun allowPermissionsIfNeeded(permissionNeeded: String) {
        clickPermissionDialogButton(
            permissionNeeded,
            PERMISSION_DIALOG_ALLOW_FOREGROUND_IDS.toPermissionButtonRegex()
        )
    }

    fun allowPermissionOneTime(permissionNeeded: String) {
        clickPermissionDialogButton(
            permissionNeeded,
            PERMISSION_DIALOG_ALLOW_ONE_TIME_IDS.toPermissionButtonRegex()
        )
    }

    fun denyPermissions(permissionRequested: String) {
        clickPermissionDialogButton(
            permissionRequested,
            PERMISSION_DIALOG_DENY_IDS.toPermissionButtonRegex()
        )
    }

    private fun clickPermissionDialogButton(permissionNeeded: String, permissionsIds: String) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !hasNeededPermission(
                    context,
                    permissionNeeded
                )
            ) {
                sleep(PERMISSIONS_DIALOG_DELAY.toLong())

                val allowPermissions = device.findObject(
                    UiSelector()
                        .clickable(true)
                        .checkable(false)
                        .resourceIdMatches(permissionsIds)
                )
                if (allowPermissions.exists()) {
                    allowPermissions.click()
                }
            }
        } catch (e: UiObjectNotFoundException) {
            Timber.e("There is no permissions dialog to interact with", e)
        }
    }

    private fun hasNeededPermission(context: Context, permissionNeeded: String): Boolean {
        val permissionStatus = checkSelfPermission(context, permissionNeeded)
        return permissionStatus == PackageManager.PERMISSION_GRANTED
    }

    private fun checkSelfPermission(context: Context, permission: String): Int {
        return context.checkPermission(
            permission,
            android.os.Process.myPid(),
            android.os.Process.myUid()
        )
    }
}