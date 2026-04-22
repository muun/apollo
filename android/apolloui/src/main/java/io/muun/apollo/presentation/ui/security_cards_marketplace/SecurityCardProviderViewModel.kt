package io.muun.apollo.presentation.ui.security_cards_marketplace

import androidx.lifecycle.ViewModel
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import io.muun.apollo.presentation.ui.security_cards_marketplace.models.MarketplaceFooter
import io.muun.apollo.presentation.ui.security_cards_marketplace.models.MarketplaceFooter.CurrentCurrency
import io.muun.apollo.presentation.ui.security_cards_marketplace.models.SecurityCardProvider
import io.muun.apollo.presentation.ui.security_cards_marketplace.models.cardCost
import io.muun.apollo.presentation.ui.security_cards_marketplace.models.shippingAndTaxesCost
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class SecurityCardProviderViewModel @AssistedInject constructor(
    @Assisted private val provider: SecurityCardProvider,
    @Assisted private val currencySelection: CurrentCurrency,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(
            provider: SecurityCardProvider,
            currencySelection: CurrentCurrency,
        ): SecurityCardProviderViewModel
    }

    sealed interface ViewState {

        data class Data(
            val provider: SecurityCardProvider,
            val footer: MarketplaceFooter
        ) : ViewState
    }

    private val _viewState = MutableStateFlow(ViewState.Data(
        provider = provider,
        footer = MarketplaceFooter(
            cardCost = provider.cardCost(provider.securityCards.first()),
            shippingAndTaxesCost = provider.shippingAndTaxesCost(provider.securityCards.first()),
            currentCurrency = currencySelection,
        )
    ))

    val viewState: StateFlow<ViewState> = _viewState

    fun preselectProviderSecurityCard(cardIndex: Int) {
        val data = _viewState.value

        _viewState.tryEmit(
            data.copy(
                footer = data.footer.copy(
                    cardCost = data.provider.cardCost(
                        data.provider.securityCards[cardIndex]
                    ),
                    shippingAndTaxesCost = data.provider.shippingAndTaxesCost(
                        data.provider.securityCards[cardIndex]
                    ),
                )
            )
        )
    }

    fun rotateCurrencyInFooterAmounts(currencySelected: CurrentCurrency) {
        val data = _viewState.value

        _viewState.tryEmit(data.copy(
            footer = data.footer.copy(
                currentCurrency = currencySelected
            )
        ))
    }
}
