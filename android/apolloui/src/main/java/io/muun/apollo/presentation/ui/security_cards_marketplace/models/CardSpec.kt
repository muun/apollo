package io.muun.apollo.presentation.ui.security_cards_marketplace.models

import android.os.Parcelable
import androidx.annotation.DrawableRes
import kotlinx.parcelize.Parcelize

@Parcelize
data class CardSpec(
    @DrawableRes val iconRes: Int,
    val label: String,
    val value: String,
) : Parcelable
