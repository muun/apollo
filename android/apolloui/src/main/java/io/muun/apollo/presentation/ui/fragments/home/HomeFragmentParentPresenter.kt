package io.muun.apollo.presentation.ui.fragments.home

import io.muun.apollo.presentation.ui.base.ParentPresenter

interface HomeFragmentParentPresenter : ParentPresenter {

    fun navigateToSecurityCenter()

    fun navigateToHighFeesExplanationScreen()

    fun navigateToOperations()
}
