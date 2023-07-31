package io.muun.apollo.presentation.ui.new_operation

import io.muun.apollo.domain.model.BitcoinUnit
import io.muun.apollo.domain.model.Contact
import io.muun.apollo.domain.model.SubmarineSwap
import io.muun.apollo.presentation.ui.base.BaseView
import newop.EnterAmountState
import newop.EnterDescriptionState

interface NewOperationView : BaseView {

    fun setInitialBitcoinUnit(bitcoinUnit: BitcoinUnit)

    fun setLoading(isLoading: Boolean)

    fun showErrorScreen(type: NewOperationErrorType)

    fun goToResolvingState()

    fun goToEnterAmountState(state: EnterAmountState, receiver: Receiver)

    fun setAmountInputError()

    fun goToEnterDescriptionState(state: EnterDescriptionState, receiver: Receiver)

    fun goToConfirmState(state: ConfirmStateViewModel, receiver: Receiver)

    fun goToEditFeeState()

    fun goToEditFeeManually()

    fun goToConfirmedFee()

    fun showAbortDialog()

    fun finishAndGoHome()

    interface Receiver {
        val swap: SubmarineSwap?
        val contact: Contact?
    }
}