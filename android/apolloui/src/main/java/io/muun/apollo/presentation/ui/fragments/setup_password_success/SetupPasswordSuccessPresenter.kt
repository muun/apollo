package io.muun.apollo.presentation.ui.fragments.setup_password_success

import io.muun.apollo.presentation.analytics.AnalyticsEvent
import io.muun.apollo.presentation.ui.base.BaseView
import io.muun.apollo.presentation.ui.base.SingleFragmentPresenter
import io.muun.apollo.presentation.ui.base.di.PerFragment
import javax.inject.Inject

@PerFragment
class SetupPasswordSuccessPresenter @Inject constructor():
    SingleFragmentPresenter<BaseView, SetupPasswordSuccessParentPresenter>() {

    override fun getEntryEvent() =
        AnalyticsEvent.S_FEEDBACK(AnalyticsEvent.FEEDBACK_TYPE.EMAIL_SETUP_SUCCESS)

    fun finishSetup() {
        parentPresenter.finishPasswordSetup()
    }

    fun goBack() {
        parentPresenter.finishPasswordSetup()
    }
}