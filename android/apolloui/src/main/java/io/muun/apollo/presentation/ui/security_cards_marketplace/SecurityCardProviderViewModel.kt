package io.muun.apollo.presentation.ui.security_cards_marketplace

import androidx.lifecycle.ViewModel
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import io.muun.apollo.domain.model.marketplace.SecurityCardMarketplace
import io.muun.apollo.presentation.ui.security_cards_marketplace.models.MarketplaceFooter
import io.muun.apollo.presentation.ui.security_cards_marketplace.models.MarketplaceFooter.CurrentCurrency
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class SecurityCardProviderViewModel @AssistedInject constructor(
    @Assisted private val provider: SecurityCardMarketplace.SecurityCardProvider,
    @Assisted private val currencySelection: CurrentCurrency,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(
            provider: SecurityCardMarketplace.SecurityCardProvider,
            currencySelection: CurrentCurrency,
        ): SecurityCardProviderViewModel
    }

    sealed interface ViewState {

        data class Data(
            val provider: SecurityCardMarketplace.SecurityCardProvider,
            val footer: MarketplaceFooter
        ) : ViewState
    }

    private val _viewState = MutableStateFlow(ViewState.Data(
        provider = provider,
        footer = MarketplaceFooter(
            cardCost = provider.cardPrice,
            cardCostInSats = provider.cardPriceInSats,
            shippingAndTaxesCost = provider.shippingPrice,
            shippingAndTaxesCostInSats = provider.shippingPriceInSats,
            currentCurrency = currencySelection,
        )
    ))

    val viewState: StateFlow<ViewState> = _viewState

    fun rotateCurrencyInFooterAmounts(currencySelected: CurrentCurrency) {
        val data = _viewState.value

        _viewState.tryEmit(data.copy(
            footer = data.footer.copy(
                currentCurrency = currencySelected
            )
        ))
    }
}
