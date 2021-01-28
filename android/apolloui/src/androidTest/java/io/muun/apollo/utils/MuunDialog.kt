package io.muun.apollo.utils

import android.content.Context
import androidx.annotation.StringRes
import androidx.test.uiautomator.UiDevice
import io.muun.apollo.R

class MuunDialog(
    override val device: UiDevice,
    override val context: Context
) : WithMuunInstrumentationHelpers {

    fun checkDisplayed(@StringRes stringResId: Int) {
        label(stringResId).await(1000)
    }

    fun pressCancel() {
        normalizedLabel(R.string.cancel).click()
    }

    fun pressAbort() {
        normalizedLabel(R.string.abort).click()
    }

    fun pressSkip() {
        normalizedLabel(R.string.setup_password_skip_yes).click()
    }
}