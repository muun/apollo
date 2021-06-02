package io.muun.apollo.presentation.ui.fragments.setup_password_accept

import android.os.Bundle
import io.muun.apollo.presentation.analytics.AnalyticsEvent
import io.muun.apollo.presentation.ui.base.SingleFragmentPresenter
import io.muun.apollo.presentation.ui.base.di.PerFragment
import javax.inject.Inject

@PerFragment
class SetupPasswordAcceptPresenter @Inject constructor():
    SingleFragmentPresenter<SetupPasswordAcceptView, SetupPasswordAcceptParentPresenter>() {

    override fun setUp(arguments: Bundle) {
        super.setUp(arguments)

        parentPresenter.watchAcceptPasswordSetupTerms()
            .compose(handleStates(view::setLoading, this::handleError))
            .let(this::subscribeTo)
    }

    fun acceptTerms() {
        parentPresenter.acceptPasswordSetupTerms()
    }

    fun goBack() {
        parentPresenter.cancelAcceptTerms()
    }

    fun reportShowPasswordInfo() {
        analytics.report(AnalyticsEvent.S_MORE_INFO(AnalyticsEvent.S_MORE_INFO_TYPE.PASSWORD))
    }

    override fun getEntryEvent() =
        AnalyticsEvent.S_FINISH_EMAIL_SETUP()

}