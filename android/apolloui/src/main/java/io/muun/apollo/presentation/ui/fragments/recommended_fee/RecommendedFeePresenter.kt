package io.muun.apollo.presentation.ui.fragments.recommended_fee

import android.os.Bundle
import io.muun.apollo.domain.analytics.AnalyticsEvent.S_MORE_INFO
import io.muun.apollo.domain.analytics.AnalyticsEvent.S_MORE_INFO_TYPE
import io.muun.apollo.domain.analytics.AnalyticsEvent.S_SELECT_FEE
import io.muun.apollo.domain.selector.BitcoinUnitSelector
import io.muun.apollo.presentation.ui.base.SingleFragmentPresenter
import io.muun.apollo.presentation.ui.base.di.PerFragment
import newop.EditFeeState
import javax.inject.Inject

@PerFragment
class RecommendedFeePresenter @Inject constructor(
    private val bitcoinUnitSel: BitcoinUnitSelector,
) : SingleFragmentPresenter<RecommendedFeeView, RecommendedFeeParentPresenter>() {

    override fun setUp(arguments: Bundle) {
        super.setUp(arguments)
        view.setBitcoinUnit(bitcoinUnitSel.get())

        parentPresenter
            .watchEditFeeState()
            .doOnNext(view::setState)
            .doOnNext(this::reportScreen)
            .let(this::subscribeTo)
    }

    fun confirmFee(selectedFeeRateInVBytes: Double) {
        parentPresenter.confirmFee(selectedFeeRateInVBytes)
    }

    fun editFeeManually() {
        parentPresenter.editFeeManually()
    }

    fun reportScreen(state: EditFeeState) {
        // TODO this is a bit duplicated in view::setState, we could refactor a bit better
        val feeWindow = state.resolved.paymentContext.feeWindow
        val feeRateFastInVBytes = state.minFeeRateForTarget(feeWindow.fastConfTarget)
        val feeRateMidInVBytes = state.minFeeRateForTarget(feeWindow.mediumConfTarget)
        val feeRateSlowInVBytes = state.minFeeRateForTarget(feeWindow.slowConfTarget)

        analytics.report(S_SELECT_FEE(feeRateFastInVBytes, feeRateMidInVBytes, feeRateSlowInVBytes))
    }

    fun reportShowSelectFeeInfo() {
        analytics.report(S_MORE_INFO(S_MORE_INFO_TYPE.SELECT_FEE))
    }
}