package io.muun.apollo.presentation.ui.security_cards

import android.content.Context
import androidx.annotation.StringRes
import io.muun.apollo.R

object SecurityCardProviderData {

    fun descriptionForUuid(context: Context, uuid: String): String {
        return context.getString(SECURITY_CARD_PROVIDERS_MAP.getValue(uuid).description)
    }
}

private data class SecurityCardProviderEntry(
    @StringRes val description: Int,
)

private val SECURITY_CARD_PROVIDERS_MAP = mapOf(
    "4b2f8a1d-7e3c-490b-a1f5-62d83e9c7b14" to SecurityCardProviderEntry(
        description = R.string.security_card_provider_constellations_description,
    ),
    "9e2c6b4a-1f8d-473e-b9a5-c3d2e1f4a7b8" to SecurityCardProviderEntry(
        description = R.string.security_card_provider_numbers_description,
    ),
    "b3f5c1d8-4a72-4e9b-8c61-d3a9e2f4b7c0" to SecurityCardProviderEntry(
        description = R.string.security_card_provider_planets_description,
    ),
)
