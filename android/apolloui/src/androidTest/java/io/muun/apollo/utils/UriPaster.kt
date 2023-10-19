package io.muun.apollo.utils

import android.content.Context
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject
import io.muun.apollo.R

class UriPaster(
    override val device: UiDevice,
    override val context: Context,
) : WithMuunInstrumentationHelpers {

    fun waitForExists(): UiObject {
        val uriPaster = id(R.id.uri_paster)
        uriPaster.assertExists()
        return uriPaster
    }
}