package io.muun.apollo.presentation.ui.fragments.ek_intro

import io.muun.apollo.presentation.analytics.AnalyticsEvent
import io.muun.apollo.presentation.ui.base.di.PerFragment
import io.muun.apollo.presentation.ui.fragments.flow_intro.FlowIntroParentPresenter
import io.muun.apollo.presentation.ui.fragments.flow_intro.FlowIntroPresenter
import io.muun.apollo.presentation.ui.fragments.flow_intro.FlowIntroView
import javax.inject.Inject

@PerFragment
class EmergencyKitIntroPresenter @Inject constructor():
    FlowIntroPresenter<FlowIntroView, FlowIntroParentPresenter>() {

    override fun reportIntroductionStep(position: Int) {
        analytics.report(AnalyticsEvent.S_EMERGENCY_KIT_SLIDES(position))
    }
}