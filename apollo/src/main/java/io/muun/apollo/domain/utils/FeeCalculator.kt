package io.muun.apollo.domain.utils


import io.muun.apollo.domain.errors.InsufficientFundsError
import io.muun.common.model.SizeForAmount
import kotlin.math.ceil

/**
 * Calculate fees using a FeeWindow, given an array of Transaction size estimations.
 */
class FeeCalculator(val satoshisPerByte: Double,
                    val sizeProgression: List<SizeForAmount>) {

    private val totalBalance by lazy {
        if (sizeProgression.isEmpty()) 0 else sizeProgression.last().amountInSatoshis
    }

    /**
     * Return the FeeCalculation for a requested amount.
     */
    fun calculate(amountInSatoshis: Long, takeFeeFromAmount: Boolean = false): Long {
        if (amountInSatoshis == 0L) {
            return 0 // a special case, for consistency
        }

        if (amountInSatoshis > totalBalance) {
            throw InsufficientFundsError() // we cannot return a meaningful result
        }

        return if (takeFeeFromAmount) {
            getFeeTakenFromAmount(amountInSatoshis)

        } else {
            getFeeTakenFromRemainingBalance(amountInSatoshis)
        }
    }

    private fun getFeeTakenFromAmount(amountInSatoshis: Long): Long {
        var feeInSatoshis = 0L

        for (sizeForAmount in sizeProgression) {
            feeInSatoshis = applyRate(sizeForAmount.sizeInBytes, satoshisPerByte)

            if (sizeForAmount.amountInSatoshis >= amountInSatoshis) {
                break // no more UTXOs needed
            }
        }

        return feeInSatoshis
    }

    private fun getFeeTakenFromRemainingBalance(amountInSatoshis: Long): Long {
        var feeInSatoshis: Long = 0

        for (sizeForAmount in sizeProgression) {
            feeInSatoshis = applyRate(sizeForAmount.sizeInBytes, satoshisPerByte)

            if (sizeForAmount.amountInSatoshis >= amountInSatoshis + feeInSatoshis) {
                break // no more UTXOs needed
            }
        }

        return feeInSatoshis
    }

    private fun applyRate(sizeInBytes: Int, satoshisPerByte: Double) =
        ceil(satoshisPerByte * sizeInBytes).toLong()
}
