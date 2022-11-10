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

    fun setBitcoinUnitToSat() {
        id(R.id.settings_bitcoin_unit).click()

        id(R.id.bitcoin_unit_sat)
    }

    fun toggleTurboChannels() {
        id(R.id.settings_lightning).click()

        id(R.id.turbo_channels_switch).click()

        dialog.pressDisable()

        device.pressBack()
    }

    fun toggleLightningDefault() {
        id(R.id.settings_lightning).click()

        id(R.id.lightning_default_switch).click()

        device.pressBack()
    }
}