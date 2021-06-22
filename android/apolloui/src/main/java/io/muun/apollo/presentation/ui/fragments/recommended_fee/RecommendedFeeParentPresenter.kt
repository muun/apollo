package io.muun.apollo.presentation.ui.fragments.recommended_fee

import io.muun.apollo.domain.model.PaymentContext
import io.muun.apollo.domain.model.PaymentRequest
import io.muun.apollo.presentation.ui.base.ParentPresenter
import rx.Observable

interface RecommendedFeeParentPresenter : ParentPresenter {

    fun watchPaymentContext(): Observable<PaymentContext>

    fun confirmFee(selectedFeeOption: Double)

    fun editFeeManually(payReq: PaymentRequest)
}