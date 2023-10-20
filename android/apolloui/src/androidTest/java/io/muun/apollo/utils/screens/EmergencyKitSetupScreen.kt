package io.muun.apollo.utils.screens

import android.content.Context
import androidx.test.uiautomator.UiDevice
import io.muun.apollo.R
import io.muun.apollo.utils.WithMuunInstrumentationHelpers

class EmergencyKitSetupScreen(
    override val device: UiDevice,
    override val context: Context,
) : WithMuunInstrumentationHelpers {


    fun doCompleteFlow(sniffActivationCode: () -> String) {

        val id = id(R.id.pager)

        // We can't use the very comfy swipeLeft since for Android 13 it's impl trigger predictive
        // back gesture, so... we swipe in some coordinates of the screen like the cavemen we are.
        val rect = id.visibleBounds
        device.swipe(rect.centerX(), rect.centerY(), rect.left, rect.centerY(), 20) // 1 page slide
        device.swipe(rect.centerX(), rect.centerY(), rect.left, rect.centerY(), 20) // 1 page slide
        device.swipe(rect.centerX(), rect.centerY(), rect.left, rect.centerY(), 20) // 1 page slide

        pressMuunButton(R.id.accept)

        sleep() // Wait a little bit for EK to be generated

        // What follows is a little flaky. The only export option we can use in this testing context
        // is send-by-email, which will open an external application. We assume that this device
        // has exactly gmail application installed, which is true for emulators and Bitrise.

        // Use the email option, give the other app a moment to open, then return to Muun:
        id(R.id.save_link_manual).click()
        muunButton(R.id.dialog_confirm).press()
        label("Gmail").click()

        sleep(3)
        backUntilExists(R.id.ek_verify_action)

        // We should be in the verification screen. Enter the code, and finish the flow:
        input(R.id.code_input).text = sniffActivationCode()
        pressMuunButton(R.id.ek_verify_action)

        pressMuunButton(R.id.single_action_action)
    }
}