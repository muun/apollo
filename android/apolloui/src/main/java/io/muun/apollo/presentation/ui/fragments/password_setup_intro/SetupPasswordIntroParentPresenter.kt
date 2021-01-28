package io.muun.apollo.presentation.ui.fragments.password_setup_intro

import io.muun.apollo.presentation.ui.base.ParentPresenter

interface SetupPasswordIntroParentPresenter: ParentPresenter {

    fun startPasswordSetup()

    fun cancelIntro()

    fun skipCreateEmail()

}