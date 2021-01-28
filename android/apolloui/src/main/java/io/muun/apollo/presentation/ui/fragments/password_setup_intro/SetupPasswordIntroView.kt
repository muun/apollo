package io.muun.apollo.presentation.ui.fragments.password_setup_intro

import io.muun.apollo.domain.model.SecurityLevel
import io.muun.apollo.presentation.ui.base.BaseView

interface SetupPasswordIntroView : BaseView {

    fun setSecurityLevel(securityLevel: SecurityLevel)
}
