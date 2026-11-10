package io.muun.apollo.presentation.ui.security_cards_full_specs

import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.lifecycle.ViewModel
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import io.muun.apollo.domain.libwallet.LibwalletClient
import io.muun.apollo.domain.model.marketplace.MarketplaceCountryCode
import io.muun.apollo.domain.model.marketplace.SecurityCardFullSpecs
import io.muun.apollo.presentation.ui.base.RxSubscriptions
import io.muun.apollo.presentation.ui.security_cards_full_specs.models.AdditionalInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class SecurityCardsFullSpecsViewModel @AssistedInject constructor(
    @Assisted("countryCode") val countryCode: MarketplaceCountryCode,
    @Assisted("cardUuid") val cardUuid: String,
    private val libwalletClient: LibwalletClient,
    rxSubscriptions: RxSubscriptions,
) : ViewModel(rxSubscriptions), RxSubscriptions by rxSubscriptions {

    @AssistedFactory
    interface Factory {
        fun create(
            @Assisted("countryCode") countryCode: MarketplaceCountryCode,
            @Assisted("cardUuid") cardUuid: String,
        ): SecurityCardsFullSpecsViewModel
    }

    sealed interface ViewState {

        data object Loading : ViewState

        data class Data(
            val fullSpecs: SecurityCardFullSpecs,
        ) : ViewState
    }

    private val _viewState = MutableStateFlow<ViewState>(ViewState.Loading)
    val viewState: StateFlow<ViewState> = _viewState

    init {
        libwalletClient.fetchSecurityCardFullSpecs(countryCode, cardUuid)
            .doOnSubscribe { _viewState.tryEmit(ViewState.Loading) }
            .subscribeTo(onError = { /* TODO: handle error */ }) { fullSpecs ->
                _viewState.tryEmit(ViewState.Data(fullSpecs = fullSpecs))
            }
    }
}

sealed interface SpecListItem {

    data class CardImage(
        val imageUrl: String,
        val cardHeight: Float,
        val cardWidth: Float,
        @ColorInt val arrowColor: Int,
    ) : SpecListItem

    data class SectionHeader(
        val title: String,
    ) : SpecListItem

    data class Row(
        @DrawableRes val iconRes: Int,
        val label: String,
        val value: String,
        val additionalInfo: AdditionalInfo? = null,
        val isMetalValue: Boolean = false,
    ) : SpecListItem
}
