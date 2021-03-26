package io.muun.apollo.presentation.ui.select_night_mode

import io.muun.apollo.domain.model.NightMode
import io.muun.apollo.presentation.ui.base.BaseView

interface SelectNightModeView : BaseView {

    fun setNightMode(nightMode: NightMode)

}