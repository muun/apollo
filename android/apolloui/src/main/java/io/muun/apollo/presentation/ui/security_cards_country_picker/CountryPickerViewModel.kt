package io.muun.apollo.presentation.ui.security_cards_country_picker

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import io.muun.apollo.domain.libwallet.LibwalletClient
import io.muun.apollo.domain.model.marketplace.MarketplaceCountryCode
import io.muun.apollo.presentation.ui.base.RxSubscriptions
import io.muun.apollo.presentation.ui.security_cards_country_picker.models.CountryInfo
import io.muun.apollo.presentation.ui.security_cards_country_picker.models.SelectableCountryInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class CountryPickerViewModel @AssistedInject constructor(
    @Assisted("selectedCountryCode") val selectedCountryCode: MarketplaceCountryCode?,
    @Assisted("providerUuid") private val providerUuid: String?,
    private val libwalletClient: LibwalletClient,
    private val applicationContext: Context,
    rxSubscriptions: RxSubscriptions,
) : ViewModel(rxSubscriptions), RxSubscriptions by rxSubscriptions {

    @AssistedFactory
    interface Factory {
        fun create(
            @Assisted("selectedCountryCode") selectedCountryCode: MarketplaceCountryCode?,
            @Assisted("providerUuid") providerUuid: String?,
        ): CountryPickerViewModel
    }

    sealed interface ViewState {

        data class Data(
            val countries: List<SelectableCountryInfo>,
        ) : ViewState

        data class NoData(
            val query: String,
        ) : ViewState
    }

    private val _countries = MutableStateFlow<List<SelectableCountryInfo>>(emptyList())

    private val _query = MutableStateFlow("")

    val viewState: StateFlow<ViewState> = combine(
        _query,
        _countries,
    ) { query, countries ->
        val filteredCountries = countries
            .filter { item ->
                query.isEmpty() || item.countryInfo.name(applicationContext).contains(
                    query,
                    ignoreCase = true,
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
        initialValue = ViewState.Data(countries = emptyList()),
    )

    init {
        // TODO: handle loading and error states
        val countriesObservable = if (providerUuid != null) {
            libwalletClient.fetchSecurityCardsCountriesByProvider(providerUuid)
        } else {
            libwalletClient.fetchSecurityCardsCountries()
        }
        countriesObservable
            .subscribeTo(onError = { /* TODO: handle error */ }) { countryCodes ->
                _countries.value = countryCodes
                    .map { countryCode ->
                        val info = CountryInfo(countryCode)
                        SelectableCountryInfo(info, info.code == selectedCountryCode)
                    }
                    .sortedBy { it.countryInfo.name(applicationContext) }
            }
    }

    fun setQuery(query: String) {
        _query.value = query
    }
}
