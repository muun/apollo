package io.muun.apollo.domain.utils


import io.muun.apollo.domain.errors.InsufficientFundsError
import io.muun.apollo.domain.model.NextTransactionSize
import kotlin.math.ceil

/**
 * Calculate fees using a FeeWindow, given an array of Transaction size estimations.
 */
class FeeCalculator(val satoshisPerByte: Double,
                    val nextTransactionSize: NextTransactionSize) {

    /**
     * Return the FeeCalculation for a requested amount, using UTXO_BALANCE.
     */
    fun calculateForCollect(amountInSatoshis: Long, takeFeeFromAmount: Boolean = false): Long {
        return calculate(amountInSatoshis, takeFeeFromAmount, nextTransactionSize.utxoBalance)
    }

    /**
     * Return the FeeCalculation for a requested amount, using USER_BALANCE.
     */
    fun calculate(amountInSatoshis: Long, takeFeeFromAmount: Boolean = false): Long {
        return calculate(amountInSatoshis, takeFeeFromAmount, nextTransactionSize.userBalance)
    }

    /**
     * Return the FeeCalculation for a requested amount, using specified balance
     * (USER_BALANCE VS UTXO_BALANCE).
     */
    private fun calculate(amountInSatoshis: Long, takeFeeFromAmount: Boolean, balance: Long): Long {
        if (amountInSatoshis == 0L) {
            return 0 // a special case, for consistency
        }

        if (amountInSatoshis > balance) {
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

        for (sizeForAmount in nextTransactionSize.sizeProgression) {
            feeInSatoshis = applyRate(sizeForAmount.sizeInBytes, satoshisPerByte)

            if (sizeForAmount.amountInSatoshis >= amountInSatoshis) {
                break // no more UTXOs needed
            }
        }

        return feeInSatoshis
    }

    private fun getFeeTakenFromRemainingBalance(amountInSatoshis: Long): Long {
        var feeInSatoshis: Long = 0

        for (sizeForAmount in nextTransactionSize.sizeProgression) {
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
