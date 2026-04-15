package io.muun.apollo.presentation.ui.security_cards_country_picker.models

data class SelectableCountryInfo(
    val countryInfo: CountryInfo,
    val isSelected: Boolean = false,
)
