package io.muun.apollo.presentation.ui.fragments.home

import io.muun.apollo.presentation.ui.base.ParentPresenter

interface HomeParentPresenter : ParentPresenter {

    fun navigateToSecurityCenter()

    fun navigateToOperations()
}
