package io.muun.apollo.presentation.ui.security_cards_card_detail

import androidx.lifecycle.ViewModel
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import io.muun.apollo.domain.libwallet.LibwalletClient
import io.muun.apollo.domain.model.marketplace.MarketplaceCountryCode
import io.muun.apollo.domain.model.marketplace.SecurityCardDetail
import io.muun.apollo.presentation.ui.base.RxSubscriptions
import io.muun.apollo.presentation.ui.security_cards_marketplace.models.MarketplaceFooter
import io.muun.apollo.presentation.ui.security_cards_marketplace.models.MarketplaceFooter.CurrentCurrency
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class CardDetailViewModel @AssistedInject constructor(
    @Assisted("countryCode") val countryCode: MarketplaceCountryCode,
    @Assisted("cardUuid") val cardUuid: String,
    @Assisted val currentCurrency: CurrentCurrency,
    private val libwalletClient: LibwalletClient,
    rxSubscriptions: RxSubscriptions,
) : ViewModel(rxSubscriptions), RxSubscriptions by rxSubscriptions {

    @AssistedFactory
    interface Factory {

        fun create(
            @Assisted("countryCode") countryCode: MarketplaceCountryCode,
            @Assisted("cardUuid") cardUuid: String,
            currentCurrency: CurrentCurrency,
        ): CardDetailViewModel
    }

    sealed interface ViewState {

        data object Loading : ViewState

        data class Data(
            val provider: SecurityCardDetail.SecurityCardProvider,
            val securityCard: SecurityCardDetail.SecurityCard,
            val footer: MarketplaceFooter,
        ) : ViewState
    }

    private val _viewState = MutableStateFlow<ViewState>(ViewState.Loading)
    val viewState: StateFlow<ViewState> = _viewState

    init {
        libwalletClient.fetchSecurityCardDetail(countryCode, cardUuid)
            .doOnSubscribe { _viewState.tryEmit(ViewState.Loading) }
            .subscribeTo(onError = { /* TODO: handle error */ }) { response ->
                _viewState.tryEmit(ViewState.Data(
                    provider = response.provider,
                    securityCard = response.card,
                    footer = MarketplaceFooter(
                        cardCost = response.provider.cardPrice,
                        cardCostInSats = response.provider.cardPriceInSats,
                        shippingAndTaxesCost = response.provider.shippingPrice,
                        shippingAndTaxesCostInSats = response.provider.shippingPriceInSats,
                        currentCurrency = currentCurrency,
                    )
                ))
            }
    }

    fun rotateCurrencyInFooterAmounts(currencySelected: CurrentCurrency) {
        val data = _viewState.value as? ViewState.Data ?: return

        _viewState.tryEmit(data.copy(
            footer = data.footer.copy(
                currentCurrency = currencySelected
            )
        ))
    }
}
