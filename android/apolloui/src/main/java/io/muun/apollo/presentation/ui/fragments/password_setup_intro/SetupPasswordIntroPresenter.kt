package io.muun.apollo.presentation.ui.fragments.password_setup_intro

import android.os.Bundle
import io.muun.apollo.domain.model.SecurityCenter
import io.muun.apollo.presentation.analytics.AnalyticsEvent
import io.muun.apollo.presentation.ui.base.SingleFragmentPresenter
import io.muun.apollo.presentation.ui.base.di.PerFragment
import javax.inject.Inject

@PerFragment
class SetupPasswordIntroPresenter @Inject constructor():
    SingleFragmentPresenter<SetupPasswordIntroView, SetupPasswordIntroParentPresenter>() {

    override fun setUp(arguments: Bundle) {
        super.setUp(arguments)

        view.setSecurityLevel(SecurityCenter(userSel.get(), userSel.emailSetupSkipped()).getLevel())
    }

    fun startSetup() {
        parentPresenter.startPasswordSetup()
    }

    fun goBack() {
        parentPresenter.cancelIntro()
    }

    fun skipEmailSetup() {
        parentPresenter.skipCreateEmail()
    }

    override fun getEntryEvent() =
        AnalyticsEvent.S_EMAIL_PRIMING()
}