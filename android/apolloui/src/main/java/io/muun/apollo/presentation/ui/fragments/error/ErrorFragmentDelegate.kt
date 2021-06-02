package io.muun.apollo.presentation.ui.fragments.error

interface ErrorFragmentDelegate {

    // Default impls. Most cases don't have all these actions, so default impl is to do nothing.

    fun handleErrorDescriptionClicked() {}

    fun handleRetry() {}

    fun handleSendReport() {}
}
