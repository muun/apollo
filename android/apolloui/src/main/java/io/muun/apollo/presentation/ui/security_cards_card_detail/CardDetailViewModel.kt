package io.muun.apollo.presentation.ui.security_cards_card_detail

import androidx.lifecycle.ViewModel
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import io.muun.apollo.presentation.ui.security_cards_marketplace.models.MarketplaceFooter
import io.muun.apollo.presentation.ui.security_cards_marketplace.models.MarketplaceFooter.CurrentCurrency
import io.muun.apollo.presentation.ui.security_cards_marketplace.models.SecurityCard
import io.muun.apollo.presentation.ui.security_cards_marketplace.models.SecurityCardProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class CardDetailViewModel @AssistedInject constructor(
    @Assisted val provider: SecurityCardProvider,
    @Assisted val card: SecurityCard,
    @Assisted val footer: MarketplaceFooter,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(
            provider: SecurityCardProvider,
            card: SecurityCard,
            footer: MarketplaceFooter,
        ): CardDetailViewModel
    }

    data class ViewState(
        val provider: SecurityCardProvider,
        val card: SecurityCard,
        val footer: MarketplaceFooter,
    )

    private val _viewState = MutableStateFlow(ViewState(provider, card, footer))
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
