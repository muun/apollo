package io.muun.apollo.utils.screens

import android.content.Context
import androidx.test.uiautomator.UiDevice
import io.muun.apollo.R
import io.muun.apollo.utils.WithMuunInstrumentationHelpers

class SettingsScreen(
    override val device: UiDevice,
    override val context: Context,
) : WithMuunInstrumentationHelpers {

    fun goToChangePassword() {
        id(R.id.settings_password).click()
    }

    fun logout() {
        id(R.id.log_out_text_view).click()
    }

    fun deleteWallet() {
        id(R.id.delete_wallet_text_view).click()
    }

    fun setBitcoinUnitToSat() {
        id(R.id.settings_bitcoin_unit).click()

        id(R.id.bitcoin_unit_sat).click()
    }

    fun toggleTurboChannels() {
        id(R.id.settings_lightning).click()

        id(R.id.turbo_channels_switch).click()

        dialog.pressDisable()

        device.pressBack()
    }

    fun turnOnReceiveLightningByDefault() {
        id(R.id.settings_lightning).click()

        id(R.id.receive_preference_value).click()

        label(R.string.receive_preference_lightning_title).click()

        device.pressBack()
    }

    fun turnOnReceiveBitcoinByDefault() {
        id(R.id.settings_lightning).click()

        id(R.id.receive_preference_value).click()

        label(R.string.receive_preference_bitcoin_title).click()

        device.pressBack()
    }

    fun turnOnUnifiedQr() {
        id(R.id.settings_lightning).click()

        id(R.id.receive_preference_value).click()

        label(R.string.receive_preference_unified).click()

        device.pressBack()
    }
}