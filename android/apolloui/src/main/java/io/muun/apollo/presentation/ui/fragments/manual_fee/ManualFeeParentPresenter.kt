package io.muun.apollo.presentation.ui.fragments.manual_fee

import io.muun.apollo.domain.model.PaymentContext
import io.muun.apollo.presentation.ui.base.ParentPresenter
import rx.Observable

interface ManualFeeParentPresenter : ParentPresenter {

    fun watchPaymentContext(): Observable<PaymentContext>

    fun confirmFee(selectedFeeOption: Double)
}