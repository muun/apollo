package io.muun.apollo.presentation.ui.fragments.recommended_fee

import android.os.Bundle
import io.muun.apollo.domain.selector.BitcoinUnitSelector
import io.muun.apollo.presentation.analytics.AnalyticsEvent
import io.muun.apollo.presentation.analytics.AnalyticsEvent.*
import io.muun.apollo.presentation.ui.base.SingleFragmentPresenter
import io.muun.apollo.presentation.ui.base.di.PerFragment
import javax.inject.Inject

@PerFragment
class RecommendedFeePresenter @Inject constructor(
    private val bitcoinUnitSel: BitcoinUnitSelector
) : SingleFragmentPresenter<RecommendedFeeView, RecommendedFeeParentPresenter>() {

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

    fun editFeeManually() {
        parentPresenter.editFeeManually()
    }

    override fun getEntryEvent(): AnalyticsEvent =
        S_SELECT_FEE()

    fun reportShowSelectFeeInfo() {
        analytics.report(S_MORE_INFO(S_MORE_INFO_TYPE.SELECT_FEE))
    }
}