package io.muun.apollo.presentation.ui.recovery_code.accept

import android.os.Bundle
import io.muun.apollo.domain.action.challenge_keys.recovery_code_setup.FinishRecoveryCodeSetupAction
import io.muun.apollo.domain.analytics.AnalyticsEvent
import io.muun.apollo.domain.analytics.AnalyticsEvent.S_FINISH_RECOVERY_CODE_CONFIRM
import io.muun.apollo.presentation.ui.base.SingleFragmentPresenter
import io.muun.apollo.presentation.ui.base.di.PerFragment
import io.muun.apollo.presentation.ui.recovery_code.SetupRecoveryCodePresenter
import javax.inject.Inject

@PerFragment
internal class AcceptRecoveryCodePresenter @Inject constructor(
    private val finishRecoveryCodeSetup: FinishRecoveryCodeSetupAction,
) : SingleFragmentPresenter<AcceptRecoveryCodeView, SetupRecoveryCodePresenter>() {

    override fun setUp(arguments: Bundle) {
        super.setUp(arguments)
        view.setTexts(userSel.get())

        finishRecoveryCodeSetup
            .state
            .compose(handleStates(view::setLoading, this::handleError))
            .doOnNext {
                parentPresenter.onSetupSuccessful()
            }
            .let(this::subscribeTo)
    }

    /**
     * Finish/Verify in Houston our new challenge key setup.
     */
    fun finishSetup() {
        finishRecoveryCodeSetup.run()
    }

    fun showAbortDialog() {
        parentPresenter.showAbortDialog()
    }

    override fun getEntryEvent(): AnalyticsEvent {
        return S_FINISH_RECOVERY_CODE_CONFIRM()
    }
}