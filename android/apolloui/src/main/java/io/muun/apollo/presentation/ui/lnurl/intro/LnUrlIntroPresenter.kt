package io.muun.apollo.presentation.ui.lnurl.intro

import io.muun.apollo.domain.action.user.UpdateUserPreferencesAction
import io.muun.apollo.domain.analytics.AnalyticsEvent
import io.muun.apollo.presentation.ui.base.BasePresenter
import io.muun.apollo.presentation.ui.base.BaseView
import io.muun.apollo.presentation.ui.base.di.PerActivity
import io.muun.apollo.presentation.ui.scan_qr.LnUrlFlow
import javax.inject.Inject

@PerActivity
class LnUrlIntroPresenter @Inject constructor(
    private val updateUserPreferences: UpdateUserPreferencesAction
): BasePresenter<BaseView>() {

    fun continueWithLnUrlFlow() {

        updateUserPreferences.run { prefs ->
            prefs.copy(seenLnurlFirstTime = true)
        }

        navigator.navigateToLnUrlWithdrawScanQr(context, LnUrlFlow.STARTED_FROM_RECEIVE)
    }

    override fun getEntryEvent(): AnalyticsEvent {
        return AnalyticsEvent.S_LNURL_FIRST_TIME()
    }
}
