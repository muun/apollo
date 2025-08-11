package io.muun.apollo.presentation.ui.new_operation

import newop.AmountInfo
import newop.BitcoinAmount
import newop.ConfirmLightningState
import newop.ConfirmState
import newop.PaymentContext
import newop.PaymentIntent
import newop.Resolved
import newop.Validated

class ConfirmStateViewModel private constructor(
    private val resolved: Resolved,
    val amountInfo: AmountInfo,
    val validated: Validated,
    val note: String,
    val update: String,
    val has2fa: Boolean,
) {

    companion object {

        fun fromConfirmState(state: ConfirmState, has2fa: Boolean) =
            ConfirmStateViewModel(
                state.resolved,
                state.amountInfo,
                state.validated,
                state.note,
                state.update,
                has2fa
            )

        fun fromConfirmLightningState(state: ConfirmLightningState, has2fa: Boolean) =
            ConfirmStateViewModel(
                state.resolved,
                state.amountInfo,
                state.validated,
                state.note,
                state.update,
                has2fa
            )
    }


    val paymentIntent: PaymentIntent
        get() =
            resolved.paymentIntent

    val paymentContext: PaymentContext
        get() =
            resolved.paymentContext

    // Yes, the impl calls a property that states fee rate is in SatsPerVByte but that is a very
    // long legacy naming error, that property's unit are satoshis per weight unit. Trust me, I'm
    // an engineer.
    val feeRateInSatsPerWeight: Double
        get() =
            amountInfo.feeRateInSatsPerVByte

    val onchainFee: BitcoinAmount
        get() =
            if (validated.swapInfo == null) {
                validated.fee

            } else {
                // We use swapInfo.onchainFee instead of validated.fee, as the latter includes the
                // off chain fee so it can be show to the user.
                validated.swapInfo.onchainFee
            }
}