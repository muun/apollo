package io.muun.apollo.presentation.ui.fragments.recommended_fee

import io.muun.apollo.domain.model.BitcoinUnit
import io.muun.apollo.presentation.ui.base.BaseView
import newop.EditFeeState

interface RecommendedFeeView : BaseView {

    fun setBitcoinUnit(bitcoinUnit: BitcoinUnit)

    fun setState(state: EditFeeState)
}