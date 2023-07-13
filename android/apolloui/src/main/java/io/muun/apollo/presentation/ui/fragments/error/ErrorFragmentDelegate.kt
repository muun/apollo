package io.muun.apollo.presentation.ui.fragments.error

import io.muun.apollo.domain.analytics.AnalyticsEvent

interface ErrorFragmentDelegate {

    // Default impls. Most cases don't have all these actions, so default impl is to do nothing.

    fun handleErrorDescriptionClicked() {}

    fun handleRetry(errorType: AnalyticsEvent.ERROR_TYPE) {}

    fun handleSendReport() {}

    fun handleBack(errorType: AnalyticsEvent.ERROR_TYPE) {}
}
