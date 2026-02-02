package io.muun.apollo.presentation.ui.fragments.manual_fee

import io.muun.apollo.presentation.ui.base.BaseView
import newop.EditFeeState

interface ManualFeeView : BaseView {

    fun setState(state: EditFeeState)
}