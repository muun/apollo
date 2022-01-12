package io.muun.apollo.presentation.ui.fragments.recommended_fee

import io.muun.apollo.presentation.ui.base.ParentPresenter
import newop.EditFeeState
import rx.Observable

interface RecommendedFeeParentPresenter : ParentPresenter {

    fun watchEditFeeState(): Observable<EditFeeState>

    fun confirmFee(selectedFeeRateInVBytes: Double)

    fun editFeeManually()
}