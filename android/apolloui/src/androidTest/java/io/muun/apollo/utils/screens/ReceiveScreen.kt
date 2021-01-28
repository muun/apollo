package io.muun.apollo.utils.screens

import android.content.Context
import androidx.test.uiautomator.UiDevice
import io.muun.apollo.R
import io.muun.apollo.utils.Clipboard
import io.muun.apollo.utils.WithMuunInstrumentationHelpers

class ReceiveScreen(
    override val device: UiDevice,
    override val context: Context
): WithMuunInstrumentationHelpers {

    val address: String get() {
        id(R.id.show_qr_copy).click()
        return Clipboard.read()
    }

}