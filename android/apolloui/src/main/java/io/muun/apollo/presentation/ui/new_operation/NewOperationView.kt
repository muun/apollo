package io.muun.apollo.presentation.ui.new_operation

import io.muun.apollo.domain.model.BitcoinUnit
import io.muun.apollo.domain.model.Contact
import io.muun.apollo.domain.model.SubmarineSwap
import io.muun.apollo.presentation.ui.base.BaseView
import newop.AmountInfo
import newop.EnterAmountState
import newop.EnterDescriptionState
import newop.Resolved
import newop.Validated

interface NewOperationView : BaseView {

    fun setBitcoinUnit(bitcoinUnit: BitcoinUnit)

    fun setLoading(isLoading: Boolean)

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