package io.muun.apollo.presentation.ui.security_cards_marketplace

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.muun.apollo.presentation.ui.security_cards_country_picker.models.CountryInfo

class SecurityCardsMarketplaceViewModelFactory(
    private val initialCountryInfo: CountryInfo,
    private val assistedFactory: SecurityCardsMarketplaceViewModel.Factory,
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return assistedFactory.create(
            initialCountryInfo = initialCountryInfo,
        ) as T
    }
}
