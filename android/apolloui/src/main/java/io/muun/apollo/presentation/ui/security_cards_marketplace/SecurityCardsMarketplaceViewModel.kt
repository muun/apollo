package io.muun.apollo.presentation.ui.security_cards_marketplace

import androidx.annotation.DrawableRes
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
            val countryInfo: CountryInfo,
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
        val viewState = _viewState.value as? ViewState.Data ?: error("State invalid")

        _viewEvent.tryEmit(
            ViewEvent.NavigateToCardDetail(
                countryInfo = viewState.country,
                provider = provider,
                securityCard = securityCard,
                footer = footer,
            )
        )
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
                mockConstellationsCard(imageRes = R.drawable.sc_constellations_scorpius),
                mockConstellationsCard(imageRes = R.drawable.sc_constellations_gemini),
                mockConstellationsCard(imageRes = R.drawable.sc_constellations_sagitarius),
                mockConstellationsCard(imageRes = R.drawable.sc_constellations_virgo),
            ),
            currencyCode = "EUR",
        ),
        SecurityCardProvider(
            name = "Numbers",
            description = "Numbers",
            securityCards = listOf(
                mockNumbersCard(imageRes = R.drawable.sc_numbers_1),
                mockNumbersCard(imageRes = R.drawable.sc_numbers_2),
                mockNumbersCard(imageRes = R.drawable.sc_numbers_3),
                mockNumbersCard(imageRes = R.drawable.sc_numbers_4),
                mockNumbersCard(imageRes = R.drawable.sc_numbers_5),
                mockNumbersCard(imageRes = R.drawable.sc_numbers_6),
                mockNumbersCard(imageRes = R.drawable.sc_numbers_7),
                mockNumbersCard(imageRes = R.drawable.sc_numbers_8),
                mockNumbersCard(imageRes = R.drawable.sc_numbers_9),
            ),
            currencyCode = "USD",
        ),
        SecurityCardProvider(
            name = "Planets",
            description = "Planets",
            securityCards = listOf(
                mockPlanetsCard(imageRes = R.drawable.sc_planets_earth),
                mockPlanetsCard(imageRes = R.drawable.sc_planets_mars),
            ),
            currencyCode = "USD",
        ),
    )
)[country.code] ?: emptyList()

private fun mockConstellationsCard(@DrawableRes imageRes: Int) = SecurityCard(
    imageRes = imageRes,
    specs = mapOf(
        "primary" to listOf(
            CardSpec(R.drawable.ic_circle_off_outline_24, "Material", "Plastic"),
            CardSpec(R.drawable.ic_circle_off_outline_24, "From", "Sky"),
            CardSpec(R.drawable.ic_clock, "Arrives in", "Already there"),
        ),
        "specifications" to listOf(
            CardSpec(iconRes = R.drawable.ic_style_24px, label = "Material", value = "Plastic"),
            CardSpec(iconRes = R.drawable.ic_aspect_ratio_24px, label = "Thickness", value = "0.8mm"),
            CardSpec(iconRes = R.drawable.ic_fitness_center_24px, label = "Weight", value = "5g"),
        ),
        "security" to listOf(
            CardSpec(
                iconRes = R.drawable.ic_code_24px,
                label = "Secure Element",
                value = "EAL 5+",
                additionalData = "The secure element is a tamper-resistant chip that stores your private "
                    + "keys and performs cryptographic operations.<br><br>"
                    + "EAL 5+ is a high level of security certification.",
            ),
            CardSpec(
                iconRes = R.drawable.ic_code_24px, label = "Firmware", value = "Designed by Muun",
                additionalData = "The firmware is the core software that governs your card's behavior. "
                    + "Built and maintained by Muun.<br><br>"
                    + "It's open source, publicly available for review. Find it and more "
                    + "information on <a href=\"https://github.com/Satochip/SatochipApplet\">GitHub</a>.",
            ),
            CardSpec(iconRes = R.drawable.ic_misc_trezor_24px, label = "Packaging", value = "Tamper resistant"),
        ),
        "delivery" to listOf(
            CardSpec(iconRes = R.drawable.ic_local_shipping_24px, label = "Shipped by", value = "Sky"),
            CardSpec(iconRes = R.drawable.ic_place_24px, label = "From", value = "Sky"),
            CardSpec(iconRes = R.drawable.ic_access_time_24px, label = "Arrives in", value = "Already there"),
            CardSpec(
                iconRes = R.drawable.ic_folder_open_24px, label = "Shipping data", value = "Under GDPR",
                additionalData = "Your shipping information is handled under GDPR regulations "
                    + "and is only used for delivery purposes.",
            ),
        ),
    )
)

private fun mockNumbersCard(@DrawableRes imageRes: Int) = SecurityCard(
    imageRes = imageRes,
    specs = mapOf(
        "primary" to listOf(
            CardSpec(R.drawable.ic_style_24px, "Material", "Plastic"),
            CardSpec(R.drawable.ic_local_shipping_24px, "From", "Math"),
            CardSpec(R.drawable.ic_access_time_24px, "Arrives in", "Already here"),
        ),
        "specifications" to listOf(
            CardSpec(iconRes = R.drawable.ic_style_24px, label = "Material", value = "Plastic"),
            CardSpec(iconRes = R.drawable.ic_aspect_ratio_24px, label = "Thickness", value = "0.8mm"),
            CardSpec(iconRes = R.drawable.ic_fitness_center_24px, label = "Weight", value = "5g"),
        ),
        "security" to listOf(
            CardSpec(
                iconRes = R.drawable.ic_code_24px,
                label = "Secure Element",
                value = "EAL 5+",
                additionalData = "The secure element is a tamper-resistant chip that stores your private "
                    + "keys and performs cryptographic operations.<br><br>"
                    + "EAL 5+ is a high level of security certification.",
            ),
            CardSpec(
                iconRes = R.drawable.ic_code_24px, label = "Firmware", value = "Designed by Muun",
                additionalData = "The firmware is the core software that governs your card's behavior. "
                    + "Built and maintained by Muun.<br><br>"
                    + "It's open source, publicly available for review. Find it and more "
                    + "information on <a href=\"https://github.com/Satochip/SatochipApplet\">GitHub</a>.",
            ),
            CardSpec(iconRes = R.drawable.ic_misc_trezor_24px, label = "Packaging", value = "Tamper resistant"),
        ),
        "delivery" to listOf(
            CardSpec(iconRes = R.drawable.ic_local_shipping_24px, label = "Shipped by", value = "Constellations"),
            CardSpec(iconRes = R.drawable.ic_place_24px, label = "From", value = "Plastic"),
            CardSpec(iconRes = R.drawable.ic_access_time_24px, label = "Arrives in", value = "Plastic"),
            CardSpec(
                iconRes = R.drawable.ic_folder_open_24px, label = "Shipping data",
                value = "Plastic",
                additionalData = "Your shipping information is handled under GDPR regulations "
                    + "and is only used for delivery purposes.",
            ),
        ),
    )
)

private fun mockPlanetsCard(@DrawableRes imageRes: Int) = SecurityCard(
    imageRes = imageRes,
    specs = mapOf(
        "primary" to listOf(
            CardSpec(R.drawable.ic_style_24px, "Material", "Metal"),
            CardSpec(R.drawable.ic_local_shipping_24px, "From", "Space"),
            CardSpec(R.drawable.ic_access_time_24px, "Arrives in", "Now"),
        ),
        "specifications" to listOf(
            CardSpec(iconRes = R.drawable.ic_style_24px, label = "Material", value = "Plastic"),
            CardSpec(iconRes = R.drawable.ic_aspect_ratio_24px, label = "Thickness", value = "0.8mm"),
            CardSpec(iconRes = R.drawable.ic_fitness_center_24px, label = "Weight", value = "5g"),
        ),
        "security" to listOf(
            CardSpec(
                iconRes = R.drawable.ic_code_24px,
                label = "Secure Element",
                value = "EAL 6+",
                additionalData = "The secure element is a tamper-resistant chip that stores your private "
                    + "keys and performs cryptographic operations.<br><br>"
                    + "EAL 6+ is a high level of security certification.",
            ),
            CardSpec(
                iconRes = R.drawable.ic_code_24px, label = "Firmware", value = "Designed by Muun",
                additionalData = "The firmware is the core software that governs your card's behavior. "
                    + "Built and maintained by Muun.<br><br>"
                    + "It's open source, publicly available for review. Find it and more "
                    + "information on <a href=\"https://github.com/Satochip/SatochipApplet\">GitHub</a>.",
            ),
            CardSpec(iconRes = R.drawable.ic_misc_trezor_24px, label = "Packaging", value = "Tamper resistant"),
        ),
        "delivery" to listOf(
            CardSpec(iconRes = R.drawable.ic_local_shipping_24px, label = "Shipped by", value = "Constellations"),
            CardSpec(iconRes = R.drawable.ic_place_24px, label = "From", value = "Plastic"),
            CardSpec(iconRes = R.drawable.ic_access_time_24px, label = "Arrives in", value = "Plastic"),
            CardSpec(
                iconRes = R.drawable.ic_folder_open_24px, label = "Shipping data",
                value = "Plastic",
                additionalData = "Your shipping information is handled under GDPR regulations "
                    + "and is only used for delivery purposes.",
            ),
        ),
    )
)
