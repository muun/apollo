package io.muun.apollo.presentation.ui.fragments.new_op_error

import io.muun.apollo.presentation.ui.base.BaseView
import io.muun.apollo.presentation.ui.new_operation.NewOperationErrorType
import io.muun.apollo.domain.model.BitcoinUnit
import io.muun.apollo.domain.model.PaymentContext
import io.muun.apollo.domain.model.PaymentRequest
import io.muun.common.Optional
import newop.BalanceErrorState
import newop.ErrorState

interface NewOperationErrorView : BaseView {

    companion object {
        const val ARG_ERROR_TYPE = "error_type"
    }

    fun setErrorType(errorType: NewOperationErrorType, errorState: ErrorState?)

    fun setBitcoinUnit(bitcoinUnit: BitcoinUnit)

    fun setBalanceErrorState(state: BalanceErrorState)
}