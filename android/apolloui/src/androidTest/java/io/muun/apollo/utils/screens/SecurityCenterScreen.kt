package io.muun.apollo.utils.screens

import android.content.Context
import androidx.test.uiautomator.UiDevice
import io.muun.apollo.R
import io.muun.apollo.utils.WithMuunInstrumentationHelpers

class SecurityCenterScreen(
    override val device: UiDevice,
    override val context: Context,
) : WithMuunInstrumentationHelpers {

    fun goToEmailAndPassword() {
        id(R.id.task_email).click()
    }

    fun goToRecoveryCode() {
        id(R.id.task_recovery_code).click()
    }

    fun goToEmergencyKit() {
        id(R.id.task_export_keys).click()
    }
}