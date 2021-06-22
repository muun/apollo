package io.muun.apollo.presentation.ui.fragments.recommended_fee

import android.os.Bundle
import io.muun.apollo.domain.model.PaymentContext
import io.muun.apollo.domain.selector.CurrencyDisplayModeSelector
import io.muun.apollo.presentation.analytics.AnalyticsEvent
import io.muun.apollo.presentation.analytics.AnalyticsEvent.*
import io.muun.apollo.presentation.ui.base.SingleFragmentPresenter
import io.muun.apollo.presentation.ui.base.di.PerFragment
import javax.inject.Inject

@PerFragment
class RecommendedFeePresenter @Inject constructor(
    private val currencyDisplayModeSel: CurrencyDisplayModeSelector
) : SingleFragmentPresenter<RecommendedFeeView, RecommendedFeeParentPresenter>() {

    override fun setUp(arguments: Bundle) {
        super.setUp(arguments)
        view.setCurrencyDisplayMode(currencyDisplayModeSel.get())

        val payReq = RecommendedFeeFragment.getPaymentRequest(arguments)
        val observable = parentPresenter
            .watchPaymentContext()
            .doOnNext { payCtx: PaymentContext ->
                view.setPaymentContext(payCtx, payReq)
            }

        subscribeTo(observable)
    }

    fun confirmFee(selectedFeeRate: Double) {
        parentPresenter.confirmFee(selectedFeeRate)
    }

    fun editFeeManually() {
        parentPresenter.editFeeManually(
            RecommendedFeeFragment.getPaymentRequest(view.argumentsBundle)
        )
    }

    override fun getEntryEvent(): AnalyticsEvent =
        S_SELECT_FEE()

    fun reportShowSelectFeeInfo() {
        analytics.report(S_MORE_INFO(S_MORE_INFO_TYPE.SELECT_FEE))
    }
}