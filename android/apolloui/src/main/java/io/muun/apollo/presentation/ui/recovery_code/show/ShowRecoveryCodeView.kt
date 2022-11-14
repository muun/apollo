package io.muun.apollo.presentation.ui.recovery_code.show

import io.muun.apollo.domain.libwallet.RecoveryCodeV2
import io.muun.apollo.presentation.ui.base.SingleFragmentView

interface ShowRecoveryCodeView : SingleFragmentView {

    fun setRecoveryCode(recoveryCode: RecoveryCodeV2)
}