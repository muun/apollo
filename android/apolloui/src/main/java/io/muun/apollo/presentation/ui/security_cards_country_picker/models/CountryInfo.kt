package io.muun.apollo.presentation.ui.security_cards_country_picker.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class CountryInfo(
    val code: String,
    val name: String,
    val flagEmoji: String,
) : Parcelable
