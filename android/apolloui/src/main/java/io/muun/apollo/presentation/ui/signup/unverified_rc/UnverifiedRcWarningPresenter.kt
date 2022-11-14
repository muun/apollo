package io.muun.apollo.presentation.ui.signup.unverified_rc

import io.muun.apollo.presentation.analytics.AnalyticsEvent
import io.muun.apollo.presentation.ui.base.BaseView
import io.muun.apollo.presentation.ui.base.ParentPresenter
import io.muun.apollo.presentation.ui.base.SingleFragmentPresenter
import javax.inject.Inject

class UnverifiedRcWarningPresenter @Inject constructor() :
    SingleFragmentPresenter<BaseView, ParentPresenter>() {

    override fun getEntryEvent(): AnalyticsEvent =
        AnalyticsEvent.S_UNVERIFIED_RC_WARNING()

    fun proceedToHome() {
        navigator.navigateToHome(context)
        view.finishActivity()
    }
}
