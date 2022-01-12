package io.muun.apollo.presentation.ui.fragments.new_op_error

import android.os.Bundle
import io.muun.apollo.domain.selector.BitcoinUnitSelector
import io.muun.apollo.presentation.ui.base.SingleFragmentPresenter
import io.muun.apollo.presentation.ui.base.di.PerFragment
import io.muun.apollo.presentation.ui.new_operation.NewOperationErrorType
import io.muun.common.utils.Preconditions
import javax.inject.Inject

@PerFragment
class NewOperationErrorPresenter @Inject constructor(
    private val bitcoinUnitSel: BitcoinUnitSelector
) : SingleFragmentPresenter<NewOperationErrorView, NewOperationErrorParentPresenter>() {

    override fun setUp(arguments: Bundle) {
        super.setUp(arguments)

        val errorType = getErrorType(arguments)
        view.setErrorType(errorType, parentPresenter.getErrorMetadata().errorState)

        // Not all new op errors need/make use of PaymentContext and/or PaymentRequest. In fact,
        // an error could occur PRIOR to any of those being fully available. So, we need do some
        // filtering... For now, we're going with whitelisting the errors we know need these two.
        if (errorType === NewOperationErrorType.INSUFFICIENT_FUNDS) {
            view.setBitcoinUnit(bitcoinUnitSel.get())

            parentPresenter
                .watchBalanceErrorState()
                .doOnNext(view::setBalanceErrorState)
                .let(this::subscribeTo)
        }
    }

    fun goHomeInDefeat() {
        parentPresenter!!.goHomeInDefeat()
    }

    fun contactSupport() {
        navigator.navigateToSendErrorFeedback(context)
    }

    fun sendErrorReport() {
        sendErrorReport(checkNotNull(parentPresenter.getErrorMetadata().errorCause))
    }

    private fun getErrorType(arguments: Bundle): NewOperationErrorType {
        val errorTypeName = arguments.getString(NewOperationErrorView.ARG_ERROR_TYPE)
        Preconditions.checkState(errorTypeName != null)
        return NewOperationErrorType.valueOf(errorTypeName!!)
    }
}