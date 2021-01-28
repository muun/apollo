package io.muun.apollo.presentation.ui.fragments.rc_only_login

import io.muun.apollo.presentation.ui.settings.RecoveryCodeView

interface RcOnlyLoginView : RecoveryCodeView {

    fun handleLegacyRecoveryCodeError()

}
