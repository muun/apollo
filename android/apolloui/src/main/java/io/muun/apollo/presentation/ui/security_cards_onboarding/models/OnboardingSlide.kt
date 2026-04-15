package io.muun.apollo.presentation.ui.security_cards_onboarding.models

import androidx.annotation.StringRes

sealed interface OnboardingSlide {

    data class Explanatory(
        @StringRes val titleRes: Int,
        @StringRes val descriptionRes: Int,
    ) : OnboardingSlide

    data class CountrySelector(
        @StringRes val titleRes: Int,
        @StringRes val descriptionRes: Int,
    ) : OnboardingSlide
}
