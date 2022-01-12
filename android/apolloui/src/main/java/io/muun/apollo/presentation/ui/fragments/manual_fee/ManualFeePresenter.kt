package io.muun.apollo.presentation.ui.fragments.manual_fee

import android.os.Bundle
import io.muun.apollo.domain.selector.BitcoinUnitSelector
import io.muun.apollo.presentation.analytics.AnalyticsEvent
import io.muun.apollo.presentation.analytics.AnalyticsEvent.*
import io.muun.apollo.presentation.ui.base.SingleFragmentPresenter
import io.muun.apollo.presentation.ui.base.di.PerFragment
import javax.inject.Inject

@PerFragment class ManualFeePresenter @Inject constructor(
    private val bitcoinUnitSel: BitcoinUnitSelector
) : SingleFragmentPresenter<ManualFeeView, ManualFeeParentPresenter>() {

    override fun setUp(arguments: Bundle) {
        super.setUp(arguments)
        view.setBitcoinUnit(bitcoinUnitSel.get())

        parentPresenter
            .watchEditFeeState()
            .doOnNext(view::setState)
            .let(this::subscribeTo)
    }

    fun confirmFee(selectedFeeRateInVBytes: Double) {
        parentPresenter.confirmFee(selectedFeeRateInVBytes)
    }

    override fun getEntryEvent(): AnalyticsEvent =
        S_MANUALLY_ENTER_FEE()

    fun reportShowManualFeeInfo() {
        analytics.report(S_MORE_INFO(S_MORE_INFO_TYPE.MANUAL_FEE))
    }
}