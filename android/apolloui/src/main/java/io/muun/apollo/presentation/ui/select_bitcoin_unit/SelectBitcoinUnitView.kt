package io.muun.apollo.presentation.ui.select_bitcoin_unit

import io.muun.apollo.domain.model.BitcoinUnit
import io.muun.apollo.presentation.ui.base.BaseView

interface SelectBitcoinUnitView: BaseView {

    fun setBitcoinUnit(bitcoinUnit: BitcoinUnit)

}