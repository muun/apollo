package io.muun.apollo.presentation.ui.security_cards_onboarding

import androidx.lifecycle.ViewModel
import io.muun.apollo.R
import io.muun.apollo.presentation.ui.security_cards_onboarding.models.OnboardingSlide
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class SecurityCardsOnboardingViewModel : ViewModel() {

    data class ViewState(
        val slides: List<OnboardingSlide>,
    )

    private val _viewState = MutableStateFlow(ViewState(slides = buildSlides()))
    val viewState: StateFlow<ViewState> = _viewState
}

private fun buildSlides() = listOf(
    OnboardingSlide.Explanatory(
        titleRes = R.string.security_cards_onboarding_slide_1_title,
        descriptionRes = R.string.security_cards_onboarding_slide_1_description,
    ),
    OnboardingSlide.Explanatory(
        titleRes = R.string.security_cards_onboarding_slide_2_title,
        descriptionRes = R.string.security_cards_onboarding_slide_2_description,
    ),
    OnboardingSlide.Explanatory(
        titleRes = R.string.security_cards_onboarding_slide_3_title,
        descriptionRes = R.string.security_cards_onboarding_slide_3_description,
    ),
    OnboardingSlide.CountrySelector(
        titleRes = R.string.security_cards_onboarding_location_title,
        descriptionRes = R.string.security_cards_onboarding_location_description,
    ),
)
