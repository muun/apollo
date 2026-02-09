package io.muun.apollo.presentation.ui.settings.flags

import io.muun.apollo.presentation.ui.base.BaseView

interface DisableFeatureFlagsView : BaseView {

    fun setSecurityCardFlagEnabled(isEnabled: Boolean)

}