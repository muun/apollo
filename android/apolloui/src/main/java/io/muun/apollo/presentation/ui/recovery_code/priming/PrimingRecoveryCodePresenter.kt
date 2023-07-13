package io.muun.apollo.presentation.ui.recovery_code.priming

import android.os.Bundle
import io.muun.apollo.domain.action.challenge_keys.recovery_code_setup.StartRecoveryCodeSetupAction
import io.muun.apollo.domain.analytics.AnalyticsEvent
import io.muun.apollo.domain.analytics.AnalyticsEvent.S_RECOVERY_CODE_PRIMING
import io.muun.apollo.presentation.ui.base.SingleFragmentPresenter
import io.muun.apollo.presentation.ui.base.di.PerFragment
import io.muun.apollo.presentation.ui.recovery_code.SetupRecoveryCodePresenter
import javax.inject.Inject

@PerFragment
internal class PrimingRecoveryCodePresenter @Inject constructor(
    private val startRecoveryCodeSetup: StartRecoveryCodeSetupAction,
) : SingleFragmentPresenter<PrimingRecoveryCodeView, SetupRecoveryCodePresenter>() {

    private var hasErrored: Boolean = false

    override fun setUp(arguments: Bundle) {
        super.setUp(arguments)
        view.setTexts(userSel.get())
    }

    override fun getEntryEvent(): AnalyticsEvent =
        S_RECOVERY_CODE_PRIMING()

    /**
     * Call when the user has touched the start button.
     */
    fun continueToShowRecoveryCode() {
        startRecoveryCodeSetup
            .state
            .compose(handleStates(view::handleLoading, this::handleError))
            .doOnNext { parentPresenter.goToRecoveryCode() }
            .let(this::subscribeTo)
        // Needed in case of error and user came back to this screen
        if (hasErrored) {
            startRecoveryCodeSetup.run(parentPresenter.recoveryCode)
        }
    }

    override fun handleError(error: Throwable) {
        this.hasErrored = true
        parentPresenter.handleError(error)
    }
}