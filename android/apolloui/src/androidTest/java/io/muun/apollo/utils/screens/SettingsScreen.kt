package io.muun.apollo.utils.screens

import android.content.Context
import androidx.test.uiautomator.UiDevice
import io.muun.apollo.R
import io.muun.apollo.utils.MuunTexts
import io.muun.apollo.utils.WithMuunInstrumentationHelpers

class SettingsScreen(
    override val device: UiDevice,
    override val context: Context
): WithMuunInstrumentationHelpers {

    fun goToChangePassword() {
        id(R.id.settings_password).click()
    }

    fun logout() {
        id(R.id.log_out_text_view).click()
    }

    fun deleteWallet() {
        id(R.id.log_out_text_view).click()
    }
}