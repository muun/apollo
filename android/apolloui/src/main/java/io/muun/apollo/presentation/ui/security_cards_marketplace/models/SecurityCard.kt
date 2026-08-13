package io.muun.apollo.presentation.ui.security_cards_marketplace.models

import android.os.Parcelable
import androidx.annotation.DrawableRes
import kotlinx.parcelize.Parcelize

/**
 * Content shown in the bottom sheet when the user taps the info icon on a spec row.
 */
@Parcelize
data class AdditionalInfo(
    @DrawableRes val iconRes: Int,
    val title: String,
    val bodyHtml: String,
) : Parcelable

@Parcelize
data class SecurityCard(
    @DrawableRes
    val imageRes: Int,
    val specs: Map<String, List<CardSpec>>,
) : Parcelable
