package io.muun.apollo.presentation.ui.security_cards_onboarding

import androidx.lifecycle.ViewModel
import io.muun.apollo.presentation.ui.security_cards_country_picker.models.CountryInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class CountrySelectionSharedViewModel : ViewModel() {

    private val _selectedCountryInfo = MutableStateFlow<CountryInfo?>(null)
    val selectedCountryInfo: StateFlow<CountryInfo?> = _selectedCountryInfo

    fun onCountrySelected(countryInfo: CountryInfo) {
        _selectedCountryInfo.value = countryInfo
    }
}
