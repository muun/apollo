package io.muun.apollo.utils

import android.content.Context
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject

class MuunButton(
    override val device: UiDevice,
    override val context: Context,
    private val button: UiObject
) : WithMuunInstrumentationHelpers {

    fun doesntExist() {
        button.assertDoesntExist()
    }

    fun waitForExists(): MuunButton {
        button.assertExists()
        return this
    }

    fun textEquals(expectedText: String): MuunButton {
        button.assertTextEquals(expectedText)
        return this
    }

    fun press() {
        button.assertEnabledAndClick()
    }
}