package io.muun.apollo.presentation.ui.security_cards_full_specs.models

import android.os.Parcelable
import androidx.annotation.DrawableRes
import kotlinx.parcelize.Parcelize

@Parcelize
data class AdditionalInfo(
    @DrawableRes val iconRes: Int,
    val title: String,
    val bodyHtml: String,
) : Parcelable
