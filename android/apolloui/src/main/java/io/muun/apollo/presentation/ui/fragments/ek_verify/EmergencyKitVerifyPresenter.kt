package io.muun.apollo.presentation.ui.fragments.ek_verify

import android.os.Bundle
import io.muun.apollo.domain.action.ek.VerifyEmergencyKitAction
import io.muun.apollo.domain.analytics.AnalyticsEvent
import io.muun.apollo.domain.errors.ek.EmergencyKitVerificationError
import io.muun.apollo.presentation.ui.base.SingleFragmentPresenter
import io.muun.apollo.presentation.ui.base.di.PerFragment
import javax.inject.Inject

@PerFragment
class EmergencyKitVerifyPresenter @Inject constructor(
    private val verifyEmergencyKit: VerifyEmergencyKitAction,
) : SingleFragmentPresenter<EmergencyKitVerifyView, EmergencyKitVerifyParentPresenter>() {

    override fun setUp(arguments: Bundle) {
        super.setUp(arguments)

        parentPresenter.refreshToolbar()

        verifyEmergencyKit.state
            .compose(handleStates(view::setLoading, this::handleError))
            .doOnNext { onVerificationSuccess() }
            .let(this::subscribeTo)
    }

    fun showHelp() {
        parentPresenter.showEmergencyKitVerifyHelp()
    }

    private fun onVerificationSuccess() {
        parentPresenter.confirmEmergencyKitVerify()
    }

    override fun handleError(error: Throwable?) {
        if (error is EmergencyKitVerificationError) {
            view.setVerificationError(error)
        } else {
            super.handleError(error)
        }
    }

    override fun getEntryEvent() =
        AnalyticsEvent.S_EMERGENCY_KIT_VERIFY()

    fun verifyCode(code: String) {
        verifyEmergencyKit.run(code, parentPresenter.getGeneratedEmergencyKit())
    }

    fun goBack() {
        parentPresenter.cancelEmergencyKitVerify()
    }

}