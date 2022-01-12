package io.muun.apollo.presentation.ui.new_operation

import io.muun.apollo.domain.model.*
import io.muun.apollo.domain.model.SubmarineSwap
import io.muun.apollo.presentation.ui.base.BaseView
import newop.*

interface NewOperationView : BaseView {

    fun setBitcoinUnit(bitcoinUnit: BitcoinUnit)

    fun setForm(form: NewOperationForm)

    fun setPaymentAnalysis(analysis: PaymentAnalysis)

    fun setLoading(isLoading: Boolean)

    fun setConnectedToNetwork(isConnected: Boolean)

    fun editFee(paymentRequest: PaymentRequest)

    fun editFeeManually(paymentRequest: PaymentRequest)

    fun confirmFee(selectedFeeRate: Double)

    fun showErrorScreen(type: NewOperationErrorType)

    fun goToResolvingState()

    fun goToEnterAmountState(state: EnterAmountState, receiver: Receiver)

    fun setAmountInputError()

    fun goToEnterDescriptionState(state: EnterDescriptionState, receiver: Receiver)

    fun goToConfirmState(state: ConfirmStateViewModel)

    fun goToEditFeeState()

    fun goToEditFeeManually()

    fun goToConfirmedFee()

    fun showAbortDialog()

    interface Receiver {
        val swap: SubmarineSwap?
        val contact: Contact?
    }

    interface ConfirmStateViewModel {
        val resolved: Resolved
        val amountInfo: AmountInfo
        val validated: Validated
        val note: String
        val receiver: Receiver
    }
}