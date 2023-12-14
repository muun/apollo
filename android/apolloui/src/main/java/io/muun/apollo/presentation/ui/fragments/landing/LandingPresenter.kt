package io.muun.apollo.presentation.ui.fragments.landing

import io.muun.apollo.domain.action.fcm.ForceFetchFcmAction
import io.muun.apollo.domain.analytics.AnalyticsEvent
import io.muun.apollo.domain.analytics.AnalyticsEvent.S_GET_STARTED
import io.muun.apollo.presentation.ui.base.SingleFragmentPresenter
import io.muun.apollo.presentation.ui.base.SingleFragmentView
import io.muun.apollo.presentation.ui.base.di.PerFragment
import io.muun.apollo.presentation.ui.signup.SignupPresenter
import javax.inject.Inject

@PerFragment
open class LandingPresenter @Inject constructor(private val forceFetchFcm: ForceFetchFcmAction) :
    SingleFragmentPresenter<SingleFragmentView, SignupPresenter>() {

    override fun onSetUpFinished() {
        super.onSetUpFinished()
        parentPresenter.resumeSignupIfStarted()
        assertGooglePlayServicesPresent()

        // "Fire and forget" attempt to retrieve FCM token so its already available at wallet
        // creation or recovery.
        forceFetchFcm.run()
    }

    /**
     * Start wallet creation flow.
     */
    fun startCreateWallet() {
        parentPresenter.startSignup()
    }

    /**
     * Start wallet recovery flow.
     */
    fun startRecoverWallet() {
        parentPresenter.startLogin()
    }

    override fun getEntryEvent(): AnalyticsEvent? {
        return S_GET_STARTED()
    }
}