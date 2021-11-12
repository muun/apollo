package io.muun.apollo.presentation.ui.fragments.tr_clock_detail

import io.muun.apollo.presentation.ui.base.BaseView

interface TaprootClockDetailView: BaseView {

    fun setTaprootCounter(blocksRemaining: Int)

}