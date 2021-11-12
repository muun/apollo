package io.muun.apollo.presentation.ui.fragments.home

import io.muun.apollo.domain.model.CurrencyDisplayMode
import io.muun.apollo.domain.model.Operation
import io.muun.apollo.presentation.ui.base.BaseView

interface HomeView : BaseView {

    fun setState(homeState: HomePresenter.HomeState)

    fun setNewOp(newOp: Operation, mode: CurrencyDisplayMode)

    fun showTooltip()
}