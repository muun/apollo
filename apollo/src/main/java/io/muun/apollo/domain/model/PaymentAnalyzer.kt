package io.muun.apollo.domain.model

import io.muun.apollo.domain.utils.FeeCalculator
import io.muun.common.Rules
import io.muun.common.utils.BitcoinUtils.DUST_IN_SATOSHIS
import kotlin.math.max

class PaymentAnalyzer(private val payCtx: PaymentContext,
                      private val payReq: PaymentRequest) {

    // Extract some vars for easy access:
    private val inputCurrency = payReq.amount!!.currency
    private val nextTransactionSize = payCtx.nextTransactionSize

    // Set up fee calculators:
    private val feeCalculator = FeeCalculator(payReq.feeInSatoshisPerByte, nextTransactionSize)
    private val minimumFeeCalculator = FeeCalculator(Rules.OP_MINIMUM_FEE_RATE, nextTransactionSize)

    // Obtain balances:
    private val totalBalanceInSatoshis = payCtx.userBalance
    private val totalUtxoBalanceInSatoshis = payCtx.utxoBalance

    // Amounts and fees we can pre-compute:
    private val originalAmountInSatoshis = payCtx.convertToSatoshis(payReq.amount!!)

    /**
     * Examine a PaymentRequest, and return a PaymentAnalysis.
     */
    fun analyze(): PaymentAnalysis {
        checkNotNull(payReq.amount)

        return if (originalAmountInSatoshis > totalBalanceInSatoshis) {
            // Case 1: user cannot pay the base amount she entered
            analyzeCannotPay()

        } else if (payReq.swap != null) {
            // Case 2: this is a SubmarineSwap, it has separate rules
            analyzeSubmarineSwap()

        } else if (payReq.takeFeeFromAmount) {
            // Case 3: regular payment, with fee taken from amount
            analyzeFeeFromAmount()

        } else {
            // Case 4: regular payment
            analyzeFeeFromRemainingBalance()
        }
    }

    private fun analyzeCannotPay(): PaymentAnalysis {
        return createAnalysis(
            amountInSatoshis = originalAmountInSatoshis,
            feeInSatoshis = null,
            totalInSatoshis = null,
            outputAmountInSatoshis = payReq.swap?.fundingOutput?.outputAmountInSatoshis,
            swapFees = payReq.swap?.fees,
            canPayWithoutFee = false,
            canPayWithSelectedFee = false,
            canPayWithMinimumFee = false
        )
    }

    private fun analyzeFeeFromAmount(): PaymentAnalysis {
        val totalInSatoshis = originalAmountInSatoshis

        val feeInSatoshis = feeCalculator
            .calculate(totalInSatoshis, takeFeeFromAmount = true)

        val minimumFeeInSats = minimumFeeCalculator
            .calculate(totalInSatoshis, takeFeeFromAmount = true)

        return createAnalysis(
            amountInSatoshis = max(0, totalInSatoshis - feeInSatoshis),
            feeInSatoshis = feeInSatoshis,
            totalInSatoshis = totalInSatoshis,
            canPayWithoutFee = true,
            canPayWithMinimumFee = (totalInSatoshis > minimumFeeInSats + DUST_IN_SATOSHIS),
            canPayWithSelectedFee = (totalInSatoshis > feeInSatoshis + DUST_IN_SATOSHIS)
        )
    }

    private fun analyzeFeeFromRemainingBalance(): PaymentAnalysis {
        val feeInSatoshis = feeCalculator.calculate(originalAmountInSatoshis)
        val totalInSatoshis = originalAmountInSatoshis + feeInSatoshis

        val minimumFeeInSatoshis = minimumFeeCalculator.calculate(originalAmountInSatoshis)
        val minimumTotalInSatoshis = originalAmountInSatoshis + minimumFeeInSatoshis

        return createAnalysis(
            amountInSatoshis = originalAmountInSatoshis,
            feeInSatoshis = feeInSatoshis,
            totalInSatoshis = totalInSatoshis,
            canPayWithoutFee = true,
            canPayWithMinimumFee = (minimumTotalInSatoshis <= totalBalanceInSatoshis),
            canPayWithSelectedFee = (totalInSatoshis <= totalBalanceInSatoshis)
        )
    }

    private fun analyzeSubmarineSwap(): PaymentAnalysis {
        checkNotNull(payReq.swap)

        return if (payReq.swap.isLend()) {
            analyzeLendSubmarineSwap()

        } else if (payReq.swap.isCollect()) {
            analyzeCollectSubmarineSwap()

        } else {
            analyzeNonDebtSubmarineSwap()
        }
    }

    private fun analyzeLendSubmarineSwap(): PaymentAnalysis {
        checkNotNull(payReq.swap)

        // When lending, the outputAmount and sweepFee fields given by Swapper must be ignored:
        val totalInSatoshis = originalAmountInSatoshis + payReq.swap.fees.lightningInSats
        var swapFees = SubmarineSwapFees(lightningInSats = payReq.swap.fees.lightningInSats)

        val canPayLightningFee = (totalInSatoshis <= totalBalanceInSatoshis)

        return createAnalysis(
            amountInSatoshis = originalAmountInSatoshis,
            feeInSatoshis = 0, // no actual transaction
            totalInSatoshis = if (canPayLightningFee) totalInSatoshis else null,
            outputAmountInSatoshis = payReq.swap.fundingOutput.outputAmountInSatoshis,
            swapFees = swapFees,
            canPayWithoutFee = (originalAmountInSatoshis <= totalBalanceInSatoshis),
            canPayWithMinimumFee = canPayLightningFee,
            canPayWithSelectedFee = canPayLightningFee
        )
    }

    private fun analyzeCollectSubmarineSwap(): PaymentAnalysis {
        checkNotNull(payReq.swap)

        val outputAmountInSatoshis = payReq.swap.fundingOutput.outputAmountInSatoshis
        val collectAmountInSatoshis = payReq.swap.fundingOutput.debtAmountInSatoshis
        val amountInSatoshis = outputAmountInSatoshis - collectAmountInSatoshis

        val expectedAmountInSat = originalAmountInSatoshis +
            payReq.swap.fees.lightningInSats +
            payReq.swap.fees.sweepInSats

        check(amountInSatoshis == expectedAmountInSat) {
            "Check failed.\n" +
                "amountInSatoshis=$amountInSatoshis\n" +
                "originalAmountInSatoshis=$originalAmountInSatoshis\n" +
                "lightningInSats=${payReq.swap.fees.lightningInSats}\n" +
                "sweepInSats=${payReq.swap.fees.sweepInSats}\n"
        }

        // For COLLECT swaps, outputAmountInSatoshis includes collectAmount so check must be
        // performed against utxoBalance
        if (outputAmountInSatoshis > totalUtxoBalanceInSatoshis) {
            // Unlike other cases, this can happen because we calculate fee for the total output
            // amount (which includes the sweep fee) and not the original payment amount. So, our
            // caller has not verified this can be payed.
            return analyzeCannotPay()
        }

        val feeInSatoshis = feeCalculator.calculateForCollect(outputAmountInSatoshis)
        val totalInSatoshis = outputAmountInSatoshis + feeInSatoshis

        val minimumFeeInSatoshis = minimumFeeCalculator.calculateForCollect(outputAmountInSatoshis)
        val minimumTotalInSatoshis = outputAmountInSatoshis + minimumFeeInSatoshis

        val totalForDisplayInSatoshis = amountInSatoshis + feeInSatoshis

        return createAnalysis(
            amountInSatoshis = originalAmountInSatoshis,
            feeInSatoshis =  feeInSatoshis,
            totalInSatoshis = totalForDisplayInSatoshis,
            outputAmountInSatoshis = outputAmountInSatoshis,
            swapFees = payReq.swap.fees,
            canPayWithoutFee = true,
            canPayWithMinimumFee = (minimumTotalInSatoshis <= totalUtxoBalanceInSatoshis),
            canPayWithSelectedFee = (totalInSatoshis <= totalUtxoBalanceInSatoshis)
        )
    }

    private fun analyzeNonDebtSubmarineSwap(): PaymentAnalysis {
        checkNotNull(payReq.swap)

        val outputAmountInSatoshis = payReq.swap.fundingOutput.outputAmountInSatoshis

        if (outputAmountInSatoshis > totalBalanceInSatoshis) {
            // Unlike other cases, this can happen because we calculate fee for the total output
            // amount (which includes the sweep fee) and not the original payment amount. So, our
            // caller has not verified this can be payed.
            return analyzeCannotPay()
        }

        val feeInSatoshis = feeCalculator.calculate(outputAmountInSatoshis)
        val totalInSatoshis = outputAmountInSatoshis + feeInSatoshis

        val minimumFeeInSatoshis = minimumFeeCalculator.calculate(outputAmountInSatoshis)
        val minimumTotalInSatoshis = outputAmountInSatoshis + minimumFeeInSatoshis

        return createAnalysis(
            amountInSatoshis = originalAmountInSatoshis,
            feeInSatoshis =  feeInSatoshis,
            totalInSatoshis = totalInSatoshis,
            outputAmountInSatoshis = outputAmountInSatoshis,
            swapFees = payReq.swap.fees,
            canPayWithoutFee = true,
            canPayWithMinimumFee = (minimumTotalInSatoshis <= totalBalanceInSatoshis),
            canPayWithSelectedFee = (totalInSatoshis <= totalBalanceInSatoshis)
        )
    }

    private fun createAnalysis(amountInSatoshis: Long,
                               feeInSatoshis: Long?,
                               totalInSatoshis: Long?,
                               outputAmountInSatoshis: Long? = null, // swap only
                               swapFees: SubmarineSwapFees? = null, // same
                               canPayWithoutFee: Boolean,
                               canPayWithSelectedFee: Boolean,
                               canPayWithMinimumFee: Boolean): PaymentAnalysis {

        return PaymentAnalysis(
            payReq,
            totalBalance = convertToBitcoinAmount(totalBalanceInSatoshis),

            amount = convertToBitcoinAmount(amountInSatoshis),
            outputAmount = convertToBitcoinAmount(outputAmountInSatoshis ?: amountInSatoshis),
            sweepFee = convertToNullableBitcoinAmount(swapFees?.sweepInSats),
            lightningFee = convertToNullableBitcoinAmount(swapFees?.lightningInSats),
            channelOpenFee = convertToNullableBitcoinAmount(swapFees?.channelOpenInSats),
            channelCloseFee = convertToNullableBitcoinAmount(swapFees?.channelCloseInSats),
            fee = convertToNullableBitcoinAmount(feeInSatoshis),
            total = convertToNullableBitcoinAmount(totalInSatoshis),

            canPayWithoutFee = canPayWithoutFee,
            canPayWithSelectedFee = canPayWithSelectedFee,
            canPayWithMinimumFee = canPayWithMinimumFee,

            rateWindow = payCtx.exchangeRateWindow
        )
    }

    private fun convertToBitcoinAmount(amountInSatoshis: Long) =
        payCtx.convertToBitcoinAmount(amountInSatoshis, inputCurrency)

    private fun convertToNullableBitcoinAmount(amountInSatoshis: Long?) =
        amountInSatoshis?.let {
            payCtx.convertToBitcoinAmount(it, inputCurrency)
        }
}