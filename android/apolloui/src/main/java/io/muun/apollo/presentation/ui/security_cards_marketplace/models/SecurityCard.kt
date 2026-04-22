package io.muun.apollo.presentation.ui.security_cards_marketplace.models

import android.os.Parcelable
import androidx.annotation.DrawableRes
import kotlinx.parcelize.Parcelize

@Parcelize
data class SecurityCard(
    @DrawableRes
    val imageRes: Int,
    val primarySpecs: List<CardSpec>,
) : Parcelable
