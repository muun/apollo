package io.muun.apollo.presentation.ui.setup_password

import io.muun.apollo.domain.model.user.User
import io.muun.apollo.presentation.ui.base.BaseView

interface SetupPasswordActivityView: BaseView {

    fun setUser(user: User)

    fun goToStep(step: SetupPasswordStep)

    fun showAbortDialog()

    fun showSkipDialog()

}