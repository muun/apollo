package io.muun.apollo.presentation.ui.fragments.ek_verify_help

import io.muun.apollo.domain.analytics.AnalyticsEvent
import io.muun.apollo.presentation.ui.base.BaseView
import io.muun.apollo.presentation.ui.base.SingleFragmentPresenter
import io.muun.apollo.presentation.ui.base.di.PerFragment
import javax.inject.Inject

@PerFragment
class EmergencyKitVerifyHelpPresenter @Inject constructor():
    SingleFragmentPresenter<BaseView, EmergencyKitVerifyHelpParentPresenter>() {

    override fun getEntryEvent(): AnalyticsEvent {
        return AnalyticsEvent.S_EMERGENCY_KIT_HELP()
    }

    fun goBack() {
        parentPresenter.cancelEmergencyKitVerifyHelp()
    }
}