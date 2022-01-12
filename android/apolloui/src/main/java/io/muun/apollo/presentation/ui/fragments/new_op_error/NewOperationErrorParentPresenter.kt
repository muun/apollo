package io.muun.apollo.presentation.ui.fragments.new_op_error

import io.muun.apollo.presentation.ui.base.ParentPresenter
import newop.BalanceErrorState
import newop.ErrorState
import rx.Observable

interface NewOperationErrorParentPresenter : ParentPresenter {

    class ErrorMetadata(
        // Return ErrorState, if applicable. For errors not detected by our state machine (e.g
        // Houston provided or unknown errors) this will be null.
        val errorState: ErrorState? = null,
        // For errors not detected by out state machine
        val errorCause: Throwable? = null
    )

    fun goHomeInDefeat()

    fun getErrorMetadata(): ErrorMetadata

    fun watchBalanceErrorState(): Observable<BalanceErrorState>

}