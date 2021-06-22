package io.muun.apollo.presentation.ui.fragments.manual_fee

import io.muun.apollo.domain.model.CurrencyDisplayMode
import io.muun.apollo.domain.model.PaymentContext
import io.muun.apollo.domain.model.PaymentRequest
import io.muun.apollo.presentation.ui.base.BaseView

internal interface ManualFeeView : BaseView {

    fun setCurrencyDisplayMode(currencyDisplayMode: CurrencyDisplayMode)

    fun setPaymentContext(paymentContext: PaymentContext, paymentRequest: PaymentRequest)
}