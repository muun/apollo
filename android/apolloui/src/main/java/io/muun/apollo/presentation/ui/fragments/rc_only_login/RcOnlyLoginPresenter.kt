package io.muun.apollo.presentation.ui.fragments.rc_only_login

import android.os.Bundle
import io.muun.apollo.domain.analytics.AnalyticsEvent
import io.muun.apollo.domain.errors.rc.InvalidCharacterRecoveryCodeError
import io.muun.apollo.domain.errors.rc.InvalidRecoveryCodeV2Error
import io.muun.apollo.domain.libwallet.errors.InvalidRecoveryCodeFormatError
import io.muun.apollo.domain.model.RecoveryCode
import io.muun.apollo.presentation.ui.base.SingleFragmentPresenter
import io.muun.apollo.presentation.ui.base.di.PerFragment
import javax.inject.Inject

@PerFragment
class RcOnlyLoginPresenter @Inject constructor() :
    SingleFragmentPresenter<RcOnlyLoginView, RcOnlyLoginParentPresenter>() {

    override fun setUp(arguments: Bundle) {
        super.setUp(arguments)

        val observable = parentPresenter
            .watchLoginWithRcOnly()
            .compose(handleStates(view::setLoading, this::handleError))

        subscribeTo(observable)
    }

    fun onRecoveryCodeEdited(recoveryCode: String) {
        view.setRecoveryCodeError(null)
        view.setConfirmEnabled(false)

        try {
            RecoveryCode.validate(recoveryCode)

            if (RecoveryCode.getRecoveryCodeVersionOrDefault(recoveryCode) == 1) {
                view.handleLegacyRecoveryCodeError()  // Special handling, involves inlined link

            } else { // It's valid and its a V2 RC ;)
                view.setConfirmEnabled(true)
            }

        } catch (error: RecoveryCode.RecoveryCodeAlphabetError) {
            view.setRecoveryCodeError(InvalidCharacterRecoveryCodeError())

        } catch (error: RecoveryCode.RecoveryCodeLengthError) {
            // Do nothing. Let the user finish typing.

        } catch (error: InvalidRecoveryCodeFormatError) {
            view.setRecoveryCodeError(InvalidCharacterRecoveryCodeError())

        } catch (error: Exception) {
            handleError(error)
        }
    }

    fun submitRecoveryCode(recoveryCode: String) {
        parentPresenter.loginWithRcOnly(recoveryCode)
    }

    override fun handleError(error: Throwable?) {
        when (error) {
            is InvalidRecoveryCodeV2Error -> view.setRecoveryCodeError(error)
            else -> super.handleError(error)
        }
    }

    fun goBack() {
        parentPresenter.cancelLoginWithRcOnly()
    }

    override fun getEntryEvent(): AnalyticsEvent {
        return AnalyticsEvent.S_SIGN_IN_WITH_RC()
    }
}
