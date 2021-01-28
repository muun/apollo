package io.muun.apollo.presentation.ui.recovery_code

import io.muun.apollo.domain.model.User
import io.muun.apollo.presentation.ui.base.SingleFragmentView

internal interface SetupRecoveryCodeView : SingleFragmentView {

    fun setUser(user: User)

    fun showAbortDialog()
}