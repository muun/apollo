package io.muun.apollo.presentation.ui.security_cards_marketplace

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.muun.apollo.presentation.ui.security_cards_marketplace.models.MarketplaceFooter.CurrentCurrency
import io.muun.apollo.presentation.ui.security_cards_marketplace.models.SecurityCardProvider

class SecurityCardProviderViewModelFactory(
    private val provider: SecurityCardProvider,
    private val currencySelection: CurrentCurrency,
    private val assistedFactory: SecurityCardProviderViewModel.Factory,
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return assistedFactory.create(
            provider = provider,
            currencySelection = currencySelection,
        ) as T
    }
}
