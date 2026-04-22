package io.muun.apollo.presentation.ui.security_cards_country_picker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class CountryPickerViewModelFactory(
    private val selectedCountryCode: String?,
    private val assistedFactory: CountryPickerViewModel.Factory,
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return assistedFactory.create(
            selectedCountryCode = selectedCountryCode,
        ) as T
    }
}
