package io.muun.apollo.presentation.ui.settings.edit_password

import io.muun.apollo.domain.analytics.AnalyticsEvent
import io.muun.apollo.domain.model.ChangePasswordStep
import io.muun.apollo.presentation.ui.base.SingleFragmentView
import io.muun.apollo.presentation.ui.base.di.PerFragment
import javax.inject.Inject


@PerFragment
class StartPasswordChangePresenter @Inject constructor() :
    BaseEditPasswordPresenter<SingleFragmentView>() {

    fun start() {
        navigateToStep(ChangePasswordStep.EXISTING_PASSWORD)
    }

    override fun getEntryEvent(): AnalyticsEvent {
        return AnalyticsEvent.S_PASSWORD_CHANGE_START()
    }
}
