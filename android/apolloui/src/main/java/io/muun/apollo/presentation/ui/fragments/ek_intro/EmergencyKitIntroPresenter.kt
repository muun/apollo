package io.muun.apollo.presentation.ui.fragments.ek_intro

import io.muun.apollo.presentation.analytics.AnalyticsEvent
import io.muun.apollo.presentation.ui.base.SingleFragmentPresenter
import io.muun.apollo.presentation.ui.base.di.PerFragment
import javax.inject.Inject

@PerFragment
class EmergencyKitIntroPresenter @Inject constructor():
    SingleFragmentPresenter<EmergencyKitIntroView, EmergencyKitIntroParentPresenter>() {

    fun confirmIntro() {
        parentPresenter.confirmEmergencyKitIntro()
    }

    fun reportExportKeysIntroStep(position: Int) {
        analytics.report(AnalyticsEvent.S_EMERGENCY_KIT_SLIDES(position))
    }
}