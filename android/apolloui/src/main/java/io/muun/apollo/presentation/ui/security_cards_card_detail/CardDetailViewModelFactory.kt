package io.muun.apollo.presentation.ui.security_cards_card_detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.muun.apollo.domain.model.marketplace.MarketplaceCountryCode
import io.muun.apollo.presentation.ui.security_cards_marketplace.models.MarketplaceFooter.CurrentCurrency

class CardDetailViewModelFactory(
    private val countryCode: MarketplaceCountryCode,
    private val cardUuid: String,
    private val currentCurrency: CurrentCurrency,
    private val assistedFactory: CardDetailViewModel.Factory,
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return assistedFactory.create(
            countryCode = countryCode,
            cardUuid = cardUuid,
            currentCurrency = currentCurrency,
        ) as T
    }
}
