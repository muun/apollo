package io.muun.apollo.presentation.ui.fragments.tr_intro

import io.muun.apollo.domain.analytics.AnalyticsEvent
import io.muun.apollo.presentation.ui.base.di.PerFragment
import io.muun.apollo.presentation.ui.fragments.flow_intro.FlowIntroPresenter
import io.muun.apollo.presentation.ui.fragments.flow_intro.FlowIntroView
import javax.inject.Inject

@PerFragment
class TaprootIntroPresenter @Inject constructor():
    FlowIntroPresenter<FlowIntroView, TaprootIntroParentPresenter>() {

    override fun reportIntroductionStep(position: Int) {
        analytics.report(AnalyticsEvent.S_TAPROOT_SLIDES(position))
    }

    fun abortIntroduction() {
        parentPresenter.abortIntroduction()
    }
}