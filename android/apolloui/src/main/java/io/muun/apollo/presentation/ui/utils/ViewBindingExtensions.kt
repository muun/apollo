package io.muun.apollo.presentation.ui.utils

import androidx.annotation.ColorRes
import androidx.viewbinding.ViewBinding

fun ViewBinding.getColorCompat(@ColorRes colorId: Int): Int {
    return root.context.getColorCompat(colorId)
}
