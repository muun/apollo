package io.muun.apollo.utils

import androidx.annotation.StringRes
import androidx.test.platform.app.InstrumentationRegistry
import io.muun.apollo.presentation.ui.utils.OS
import java.util.Locale

object MuunTexts {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    fun normalize(@StringRes id: Int): String {
        val text = context.getString(id)
        return if (OS.shouldNormalizeTextForUiTests()) text.uppercase(Locale.getDefault()) else text
    }
}