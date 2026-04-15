package io.muun.apollo.presentation.ui.security_cards_country_picker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import io.muun.apollo.presentation.ui.security_cards_country_picker.models.CountryInfo
import io.muun.apollo.presentation.ui.security_cards_country_picker.models.SelectableCountryInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class CountryPickerViewModel @AssistedInject constructor(
    @Assisted val selectedCountryCode: String?,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(selectedCountryCode: String?): CountryPickerViewModel
    }

    sealed interface ViewState {

        data class Data(
            val countries: List<SelectableCountryInfo>,
        ) : ViewState

        data class NoData(
            val query: String,
        ) : ViewState
    }

    private val countries: List<SelectableCountryInfo> = mockCountriesData(selectedCountryCode)

    private val _query = MutableStateFlow("")

    val viewState: StateFlow<ViewState> = combine(
        _query,
    ) { (query) ->
        val filteredCountries = countries
            .filter { selectableCountryInfo ->
                query.isEmpty() || selectableCountryInfo.countryInfo.name.contains(
                    query,
                    ignoreCase = true
                )
            }

        if (filteredCountries.isEmpty()) {
            ViewState.NoData(query)
        } else {
            ViewState.Data(countries = filteredCountries)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Lazily,
        initialValue = ViewState.Data(
            countries = countries,
        ),
    )

    fun setQuery(query: String) {
        _query.value = query
    }
}

// Mocking stuff, not real implementation.
private fun mockCountriesData(selectedCountryCode: String?): List<SelectableCountryInfo> = listOf(
    CountryInfo("AR", "Argentina", "\uD83C\uDDE6\uD83C\uDDF7"),
    CountryInfo("AU", "Australia", "\uD83C\uDDE6\uD83C\uDDFA"),
    CountryInfo("AT", "Austria", "\uD83C\uDDE6\uD83C\uDDF9"),
    CountryInfo("BE", "Belgium", "\uD83C\uDDE7\uD83C\uDDEA"),
    CountryInfo("BR", "Brazil", "\uD83C\uDDE7\uD83C\uDDF7"),
    CountryInfo("CA", "Canada", "\uD83C\uDDE8\uD83C\uDDE6"),
    CountryInfo("CO", "Colombia", "\uD83C\uDDE8\uD83C\uDDF4"),
    CountryInfo("CZ", "Czech Republic", "\uD83C\uDDE8\uD83C\uDDFF"),
    CountryInfo("FR", "France", "\uD83C\uDDEB\uD83C\uDDF7"),
    CountryInfo("DE", "Germany", "\uD83C\uDDE9\uD83C\uDDEA"),
    CountryInfo("HU", "Hungary", "\uD83C\uDDED\uD83C\uDDFA"),
    CountryInfo("IT", "Italy", "\uD83C\uDDEE\uD83C\uDDF9"),
    CountryInfo("MX", "Mexico", "\uD83C\uDDF2\uD83C\uDDFD"),
    CountryInfo("NL", "Netherlands", "\uD83C\uDDF3\uD83C\uDDF1"),
    CountryInfo("ES", "Spain", "\uD83C\uDDEA\uD83C\uDDF8"),
    CountryInfo("CH", "Switzerland", "\uD83C\uDDE8\uD83C\uDDED"),
    CountryInfo("GB", "United Kingdom", "\uD83C\uDDEC\uD83C\uDDE7"),
    CountryInfo("US", "United States", "\uD83C\uDDFA\uD83C\uDDF8"),
)
    .map { SelectableCountryInfo(countryInfo = it, isSelected = it.code == selectedCountryCode) }
    .sortedBy { it.countryInfo.name }
