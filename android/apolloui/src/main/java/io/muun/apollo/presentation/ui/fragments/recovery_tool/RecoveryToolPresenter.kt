package io.muun.apollo.presentation.ui.fragments.recovery_tool

import io.muun.apollo.presentation.analytics.AnalyticsEvent
import io.muun.apollo.presentation.ui.base.BaseView
import io.muun.apollo.presentation.ui.base.ParentPresenter
import io.muun.apollo.presentation.ui.base.SingleFragmentPresenter
import io.muun.apollo.presentation.ui.base.di.PerFragment
import javax.inject.Inject

@PerFragment
class RecoveryToolPresenter @Inject constructor():
    SingleFragmentPresenter<BaseView, ParentPresenter>() {

    override fun getEntryEvent(): AnalyticsEvent {
        return AnalyticsEvent.S_EXPORT_KEYS_RECOVERY_TOOL()
    }
}