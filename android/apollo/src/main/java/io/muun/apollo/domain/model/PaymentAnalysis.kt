package io.muun.apollo.domain.model

import io.muun.common.Rules
import io.muun.common.utils.BitcoinUtils
import io.muun.common.utils.Preconditions.checkNonNegative

/**
 * The result of analyzing a PaymentRequest, performing validation and currency conversions.
 */
class PaymentAnalysis(
    /** The original payment request. */
    val payReq: PaymentRequest,

    /** The total balance in the wallet. */
    val totalBalance: BitcoinAmount,

    /** The amount the PaymentRequest intends to pay. */
    val amount: BitcoinAmount,

    /** The potentially adjusted amount that should go in the transaction output */
    val outputAmount: BitcoinAmount,

    /** The fee Muun charges to claim the payment of a SubmarineSwap. */
    val sweepFee: BitcoinAmount?,

    /** The fee charged by the LN nodes in the route to pay an invoice */
    val lightningFee: BitcoinAmount?,

    /** The transaction fee for the PaymentRequest, null if `canPayWithoutFee` is false. */
    val fee: BitcoinAmount?,

    /**
     * The total money to be spent, null if `canPayWithSelectedFee` is false.
     * UPDATE: This is total BS. Sometimes canPayWithSelectedFee is false and total is not null.
     * E.g for UseAllFunds/TFFA this can happen.
     */
    val total: BitcoinAmount?,

    /** Whether the amount could itself be paid, ignoring fees. */
    val canPayWithoutFee: Boolean,

    /** Whether the amount can be paid with the selected fee rate. */
    val canPayWithSelectedFee: Boolean,

    /** Whether the user could pay for this PaymentRequest, if she selected the minimum fee. */
    val canPayWithMinimumFee: Boolean,

    /** The exchange rate window in use. */
    val rateWindow: ExchangeRateWindow
) {

    /** Whether the payment requires an on-chain transaction */
    val hasOnChainTransaction =
        !(payReq.swap?.isLend() ?: false) // only true for lend swaps (sorry for the logic)

    /** Whether the amount entered (when an on-chain transaction is needed) is below dust. */
    val isAmountTooSmall =
        hasOnChainTransaction &&
            checkNonNegative(outputAmount.inSatoshis) < BitcoinUtils.DUST_IN_SATOSHIS

    /** Whether the description length is below the mininum set by Rules. */
    val isDescriptionTooShort =
        payReq.description == null || payReq.description.length < Rules.OP_DESCRIPTION_MIN_LENGTH

    /** Whether this analysis concluded that the payment request is entirely valid. */
    fun isValid() =
        !isAmountTooSmall && !isDescriptionTooShort && canPayWithSelectedFee
}