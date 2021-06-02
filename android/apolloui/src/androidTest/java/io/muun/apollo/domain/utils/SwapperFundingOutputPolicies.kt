package io.muun.apollo.domain.utils

import io.muun.common.model.BtcAmount
import io.muun.common.model.DebtType
import io.muun.common.utils.BitcoinUtils

class SwapperFundingOutputPolicies(
    val maxDebtInSat: Long = 0,
    val potentialCollectInSat: Long = 0,
    val maxAmountInSatFor0Conf: Long = 0
) {

    /**
     * Decide whether the debt policy to use for a swap (LEND, COLLECT or NONE).
     */
    fun getDebtType(paymentAmountInSat: Long,
                    lightningFeeInSat: Long,
                    hasKnownFundingTx: Boolean): DebtType {
        val numConfirmations = getFundingConfirmations(paymentAmountInSat, lightningFeeInSat)
        val totalAmountInSat = paymentAmountInSat + lightningFeeInSat
        if (!hasKnownFundingTx && numConfirmations == 0 && totalAmountInSat <= maxDebtInSat) {
            return DebtType.LEND
        }
        return if (potentialCollectInSat > 0) {
            DebtType.COLLECT
        } else DebtType.NONE
    }

    /**
     * Decide how much debt to issue / collect for a swap.
     */
    fun getDebtAmount(paymentAmountInSat: Long,
                      lightningFeeInSat: Long,
                      hasKnownFundingTx: Boolean): BtcAmount {
        return when (getDebtType(paymentAmountInSat, lightningFeeInSat, hasKnownFundingTx)) {
            DebtType.LEND -> BtcAmount.fromSats(paymentAmountInSat + lightningFeeInSat)
            DebtType.COLLECT -> BtcAmount.fromSats(potentialCollectInSat)
            DebtType.NONE -> BtcAmount.ZERO
            else -> BtcAmount.ZERO
        }
    }

    /**
     * Decide whether a swap qualifies for 0-conf.
     */
    fun getFundingConfirmations(paymentAmountInSat: Long, lightningFeeInSat: Long): Int {
        val totalAmountInSat = paymentAmountInSat + lightningFeeInSat
        val is0Conf = totalAmountInSat <= maxAmountInSatFor0Conf
        return if (is0Conf) 0 else 1
    }

    /**
     * Compute the minimum amount that the user should pay in order to perform the swap.
     */
    private fun getMinFundingAmountInSat(paymentAmountInSat: Long,
                                         lightningFeeInSat: Long,
                                         hasKnownFundingTx: Boolean): Long {
        var inputAmountInSat = paymentAmountInSat + lightningFeeInSat
        val debtType = getDebtType(paymentAmountInSat, lightningFeeInSat, hasKnownFundingTx)
        if (debtType == DebtType.COLLECT) {
            inputAmountInSat += getDebtAmount(
                paymentAmountInSat, lightningFeeInSat, hasKnownFundingTx
            ).toSats()
        }
        return inputAmountInSat
    }

    /**
     * Compute the amount that the user should pay in the funding output.
     */
    fun getFundingOutputAmount(paymentAmountInSat: Long,
                               lightningFeeInSat: Long,
                               hasKnownFundingTx: Boolean): BtcAmount {
        val minAmountInSat = getMinFundingAmountInSat(
            paymentAmountInSat, lightningFeeInSat, hasKnownFundingTx
        )
        val outputAmountInSat = Math.max(minAmountInSat, BitcoinUtils.DUST_IN_SATOSHIS)
        return BtcAmount.fromSats(outputAmountInSat)
    }

    /**
     * Compute the padding used in the output amount in order to reach the minimum DUST amount.
     */
    fun getFundingOutputPaddingInSat(paymentAmountInSat: Long,
                                     lightningFeeInSat: Long,
                                     hasKnownFundingTx: Boolean): Long {
        val minAmountInSat = getMinFundingAmountInSat(
            paymentAmountInSat, lightningFeeInSat, hasKnownFundingTx
        )
        val outputAmount = getFundingOutputAmount(
            paymentAmountInSat, lightningFeeInSat, hasKnownFundingTx
        )
        return outputAmount.toSats() - minAmountInSat
    }

}