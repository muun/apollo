package io.muun.apollo.presentation.ui.security_cards_marketplace

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.muun.apollo.presentation.ui.security_cards_marketplace.models.MarketplaceFooter.CurrentCurrency

class CurrencySelectionSharedViewModelFactory(
    private val initialCurrentCurrency: CurrentCurrency,
    private val assistedFactory: CurrencySelectionSharedViewModel.Factory,
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return assistedFactory.create(
            initialCurrentCurrency = initialCurrentCurrency,
        ) as T
    }
}
