package io.muun.apollo.presentation.ui.security_cards_marketplace

import androidx.lifecycle.ViewModel
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import io.muun.apollo.domain.libwallet.LibwalletClient
import io.muun.apollo.domain.model.marketplace.SecurityCardMarketplace
import io.muun.apollo.presentation.ui.base.RxSubscriptions
import io.muun.apollo.presentation.ui.security_cards_country_picker.models.CountryInfo
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

class SecurityCardsMarketplaceViewModel @AssistedInject constructor(
    @Assisted val initialCountryInfo: CountryInfo,
    private val libwalletClient: LibwalletClient,
    rxSubscriptions: RxSubscriptions,
) : ViewModel(rxSubscriptions), RxSubscriptions by rxSubscriptions {

    @AssistedFactory
    interface Factory {
        fun create(initialCountryInfo: CountryInfo): SecurityCardsMarketplaceViewModel
    }

    sealed interface ViewState {

        data class Loading(
            val country: CountryInfo,
        ) : ViewState

        data class Data(
            val country: CountryInfo,
            val providers: List<SecurityCardMarketplace.SecurityCardProvider>,
        ) : ViewState

        data class NoData(
            val country: CountryInfo,
        ) : ViewState
    }

    sealed interface ViewEvent {

        data class NavigateToCardDetail(
            val providerUuid: String,
            val cardUuid: String,
        ) : ViewEvent
    }

    private val _viewState = MutableStateFlow<ViewState>(ViewState.Loading(initialCountryInfo))
    val viewState: StateFlow<ViewState> = _viewState

    private val _viewEvent = MutableSharedFlow<ViewEvent>(replay = 0, extraBufferCapacity = 1)
    val viewEvent: SharedFlow<ViewEvent> = _viewEvent

    init {
        fetchMarketplace(initialCountryInfo)
    }

    fun changeCountry(newCountry: CountryInfo) {
        fetchMarketplace(newCountry)
    }

    fun continueWithSecurityCard(cardUuid: String) {
        val data = _viewState.value as? ViewState.Data ?: return
        val provider = data.providers.find { provider ->
            provider.securityCards.any { card -> card.uuid == cardUuid }
        } ?: return

        _viewEvent.tryEmit(
            ViewEvent.NavigateToCardDetail(
                providerUuid = provider.uuid,
                cardUuid = cardUuid
            )
        )
    }

    private fun fetchMarketplace(country: CountryInfo) {
        libwalletClient.fetchSecurityCardsMarketplace(country.code)
            .doOnSubscribe { _viewState.tryEmit(ViewState.Loading(country)) }
            .subscribeTo(
                replacing = ::fetchMarketplace,
                onError = { /* TODO: handle error */ },
            ) { marketplace ->
                if (marketplace.providers.isNotEmpty()) {
                    _viewState.tryEmit(ViewState.Data(
                        country = country,
                        providers = marketplace.providers,
                    ))
                } else {
                    _viewState.tryEmit(ViewState.NoData(country = country))
                }
            }
    }
}
