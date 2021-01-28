package io.muun.apollo.utils

import android.content.Context
import androidx.test.uiautomator.UiDevice
import io.muun.apollo.R

class MuunToolbar(
    override val device: UiDevice,
    override val context: Context
) : WithMuunInstrumentationHelpers {

    fun pressClose() {
        // TODO maybe unreliable? There's no "right" way to access this (even less to assert if it's
        //  a close or back button, since only difference is the image resource loaded). Other ideas:
        // - target first child of toolbar (targetable by id)
        // - target (first?) child of class ImageButton of toolbar
        // - use espresso which will also resort to some of these strategies
        desc(R.string.abc_action_bar_up_description).click()
    }

    fun pressBack() {
        // As explained above there's currently no way to differentiate which button is showing
        pressClose()
    }
}