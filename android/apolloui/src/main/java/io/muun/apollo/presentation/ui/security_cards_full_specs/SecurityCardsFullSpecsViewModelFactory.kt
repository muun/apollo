package io.muun.apollo.presentation.ui.security_cards_full_specs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.muun.apollo.domain.model.marketplace.MarketplaceCountryCode

class SecurityCardsFullSpecsViewModelFactory(
    private val countryCode: MarketplaceCountryCode,
    private val cardUuid: String,
    private val assistedFactory: SecurityCardsFullSpecsViewModel.Factory,
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return assistedFactory.create(
            countryCode = countryCode,
            cardUuid = cardUuid,
        ) as T
    }
}
