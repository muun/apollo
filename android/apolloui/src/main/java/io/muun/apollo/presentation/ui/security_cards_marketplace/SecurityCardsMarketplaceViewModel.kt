package io.muun.apollo.presentation.ui.security_cards_marketplace

import androidx.lifecycle.ViewModel
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import io.muun.apollo.R
import io.muun.apollo.presentation.ui.security_cards_country_picker.models.CountryInfo
import io.muun.apollo.presentation.ui.security_cards_marketplace.models.CardSpec
import io.muun.apollo.presentation.ui.security_cards_marketplace.models.MarketplaceFooter
import io.muun.apollo.presentation.ui.security_cards_marketplace.models.SecurityCard
import io.muun.apollo.presentation.ui.security_cards_marketplace.models.SecurityCardProvider
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

class SecurityCardsMarketplaceViewModel @AssistedInject constructor(
    @Assisted val initialCountryInfo: CountryInfo,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(initialCountryInfo: CountryInfo): SecurityCardsMarketplaceViewModel
    }

    sealed interface ViewState {

        data class Data(
            val country: CountryInfo,
            val providers: List<SecurityCardProvider>,
        ) : ViewState

        data class NoData(
            val country: CountryInfo,
        ) : ViewState
    }

    sealed interface ViewEvent {

        data class NavigateToCardDetail(
            val provider: SecurityCardProvider,
            val securityCard: SecurityCard,
            val footer: MarketplaceFooter,
        ) : ViewEvent
    }

    private val _viewState =
        MutableStateFlow<ViewState>(
            mockCardsData(initialCountryInfo).let { securityCards ->
                if (securityCards.isEmpty()) {
                    ViewState.NoData(country = initialCountryInfo)
                } else {
                    ViewState.Data(
                        country = initialCountryInfo,
                        providers = securityCards,
                    )
                }
            }
        )
    val viewState: StateFlow<ViewState> = _viewState

    private val _viewEvent = MutableSharedFlow<ViewEvent>(replay = 0, extraBufferCapacity = 1)
    val viewEvent: SharedFlow<ViewEvent> = _viewEvent


    fun changeCountry(newCountry: CountryInfo) {
        val providers = mockCardsData(newCountry)

        if (providers.isNotEmpty()) {
            _viewState.tryEmit(ViewState.Data(
                country = newCountry,
                providers = providers,
            ))
        } else {
            _viewState.tryEmit(ViewState.NoData(
                country = newCountry,
            ))
        }
    }

    fun continueWithSecurityCard(
        provider: SecurityCardProvider,
        securityCard: SecurityCard,
        footer: MarketplaceFooter,
    ) {
        _viewEvent.tryEmit(ViewEvent.NavigateToCardDetail(
            provider = provider,
            securityCard = securityCard,
            footer = footer,
        ))
    }
}

private fun mockCardsData(
    country: CountryInfo,
) = mapOf(
    "BE" to listOf(
        SecurityCardProvider(
            name = "Constellations",
            description = "Constellations",
            securityCards = listOf(
                SecurityCard(imageRes = R.drawable.sc_constellations_scorpius, primarySpecs = mockCardSpecs()),
                SecurityCard(imageRes = R.drawable.sc_constellations_gemini, primarySpecs = mockCardSpecs()),
                SecurityCard(imageRes = R.drawable.sc_constellations_sagitarius, primarySpecs = mockCardSpecs()),
                SecurityCard(imageRes = R.drawable.sc_constellations_virgo, primarySpecs = mockCardSpecs()),
            ),
            currencyCode = "EUR",
        ),
        SecurityCardProvider(
            name = "Numbers",
            description = "Numbers",
            securityCards = listOf(
                SecurityCard(imageRes = R.drawable.sc_numbers_1, primarySpecs = mockCardSpecs()),
                SecurityCard(imageRes = R.drawable.sc_numbers_2, primarySpecs = mockCardSpecs()),
                SecurityCard(imageRes = R.drawable.sc_numbers_3, primarySpecs = mockCardSpecs()),
                SecurityCard(imageRes = R.drawable.sc_numbers_4, primarySpecs = mockCardSpecs()),
                SecurityCard(imageRes = R.drawable.sc_numbers_5, primarySpecs = mockCardSpecs()),
                SecurityCard(imageRes = R.drawable.sc_numbers_6, primarySpecs = mockCardSpecs()),
                SecurityCard(imageRes = R.drawable.sc_numbers_7, primarySpecs = mockCardSpecs()),
                SecurityCard(imageRes = R.drawable.sc_numbers_8, primarySpecs = mockCardSpecs()),
                SecurityCard(imageRes = R.drawable.sc_numbers_9, primarySpecs = mockCardSpecs()),
            ),
            currencyCode = "USD",
        ),
        SecurityCardProvider(
            name = "Planets",
            description = "Planets",
            securityCards = listOf(
                SecurityCard(imageRes = R.drawable.sc_planets_earth, primarySpecs = mockCardSpecs()),
                SecurityCard(imageRes = R.drawable.sc_planets_mars, primarySpecs = mockCardSpecs()),
            ),
            currencyCode = "USD",
        ),
    )
)[country.code] ?: emptyList()

private fun mockCardSpecs() = listOf(
    CardSpec(
        iconRes = R.drawable.ic_clock,
        label = "Delivers in",
        value = "15-30 days",
    ),
    CardSpec(
        iconRes = R.drawable.ic_circle_off_outline_24,
        label = "Ships from",
        value = "Belgium",
    ),
    CardSpec(
        iconRes = R.drawable.ic_circle_off_outline_24,
        label = "Material",
        value = "Plastic",
    ),
)
