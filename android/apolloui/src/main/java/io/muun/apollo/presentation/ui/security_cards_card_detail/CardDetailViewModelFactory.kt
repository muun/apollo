package io.muun.apollo.presentation.ui.security_cards_card_detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.muun.apollo.presentation.ui.security_cards_marketplace.models.MarketplaceFooter
import io.muun.apollo.presentation.ui.security_cards_marketplace.models.SecurityCard
import io.muun.apollo.presentation.ui.security_cards_marketplace.models.SecurityCardProvider

class CardDetailViewModelFactory(
    private val provider: SecurityCardProvider,
    private val card: SecurityCard,
    private val footer: MarketplaceFooter,
    private val assistedFactory: CardDetailViewModel.Factory,
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return assistedFactory.create(
            provider = provider,
            card = card,
            footer = footer,
        ) as T
    }
}
