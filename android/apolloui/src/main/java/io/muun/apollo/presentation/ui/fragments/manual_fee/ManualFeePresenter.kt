package io.muun.apollo.presentation.ui.fragments.manual_fee

import android.os.Bundle
import io.muun.apollo.domain.model.PaymentContext
import io.muun.apollo.domain.selector.CurrencyDisplayModeSelector
import io.muun.apollo.presentation.analytics.AnalyticsEvent
import io.muun.apollo.presentation.analytics.AnalyticsEvent.*
import io.muun.apollo.presentation.ui.base.SingleFragmentPresenter
import io.muun.apollo.presentation.ui.base.di.PerFragment
import javax.inject.Inject

@PerFragment
internal class ManualFeePresenter @Inject constructor(
    private val currencyDisplayModeSel: CurrencyDisplayModeSelector
) : SingleFragmentPresenter<ManualFeeView, ManualFeeParentPresenter>() {

    override fun setUp(arguments: Bundle) {
        super.setUp(arguments)
        view.setCurrencyDisplayMode(currencyDisplayModeSel.get())

        val payReq = ManualFeeFragment.getPaymentRequest(arguments)
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

    override fun getEntryEvent(): AnalyticsEvent =
        S_MANUALLY_ENTER_FEE()

    fun reportShowManualFeeInfo() {
        analytics.report(S_MORE_INFO(S_MORE_INFO_TYPE.MANUAL_FEE))
    }
}