package io.muun.apollo.presentation.ui.security_cards.utils

import android.content.res.ColorStateList
import androidx.annotation.ColorInt
import com.google.android.material.button.MaterialButton
import io.muun.apollo.R
import io.muun.apollo.presentation.ui.utils.getColorCompat

/**
 * Overrides the background color of a button styled with muun_button_primary, keeping the
 * default disabled state color.
 */
fun MaterialButton.setPrimaryButtonColor(@ColorInt color: Int) {
    backgroundTintList = ColorStateList(
        arrayOf(
            intArrayOf(-android.R.attr.state_enabled),
            intArrayOf(),
        ),
        intArrayOf(
            context.getColorCompat(R.color.muun_button_primary_bg_disabled),
            color,
        ),
    )
}
