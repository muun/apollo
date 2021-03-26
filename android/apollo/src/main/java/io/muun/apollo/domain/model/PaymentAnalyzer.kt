package io.muun.apollo.domain.model

import io.muun.apollo.domain.utils.FeeCalculator
import io.muun.common.Rules
import io.muun.common.model.DebtType
import io.muun.common.utils.BitcoinUtils.DUST_IN_SATOSHIS
import io.muun.common.utils.Preconditions
import kotlin.math.max

class PaymentAnalyzer(private val payCtx: PaymentContext,
                      private val payReq: PaymentRequest) {

    // Extract some vars for easy access:
    private val inputCurrency = payReq.amount!!.currency
    private val nextTransactionSize = payCtx.nextTransactionSize

    // Set up fee calculators:
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
            // E.g it can't takeFeeFromAmount since that would change the amount and we only
            // support ln invoices with (a fixed) amount
            analyzeSubmarineSwap()

        } else if (payReq.takeFeeFromAmount) {
            // Case 3: regular payment, with fee taken from amount
            analyzeFeeFromAmount()

        } else {
            // Case 4: regular payment
            analyzeFeeFromRemainingBalance()
        }
    }

    private fun analyzeCannotPay(payReq: PaymentRequest = this.payReq): PaymentAnalysis {
        return createAnalysis(
            amountInSatoshis = originalAmountInSatoshis,
            feeInSatoshis = null,
            totalInSatoshis = null,
            outputAmountInSatoshis = payReq.swap?.fundingOutput?.outputAmountInSatoshis,
            swapFees = payReq.swap?.fees,
            canPayWithoutFee = originalAmountInSatoshis < totalBalanceInSatoshis,
            canPayWithSelectedFee = false,
            canPayWithMinimumFee = false
        )
    }

    private fun analyzeFeeFromAmount(): PaymentAnalysis {
        checkNotNull(payReq.feeInSatoshisPerByte)
        check(payReq.takeFeeFromAmount)

        // TODO UseAllFunds shouldn't need amount, take userBalance from payCtx
        val totalInSatoshis = originalAmountInSatoshis

        val feeInSatoshis = FeeCalculator(payReq.feeInSatoshisPerByte, nextTransactionSize)
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
        checkNotNull(payReq.feeInSatoshisPerByte)

        val feeInSatoshis = FeeCalculator(payReq.feeInSatoshisPerByte, nextTransactionSize)
                .calculate(originalAmountInSatoshis)
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

        if (payReq.swap.bestRouteFees == null) {

            // Handle fixed amount invoices
            return analyzeFixedAmountSubmarineSwap(payReq)

        } else {
            // Handle AmountLess invoices

            // Amount has been chosen by user, check that we have the corresponding optional fields
            // set for computing the fee.
            checkNotNull(payReq.swap.bestRouteFees)
            checkNotNull(payReq.swap.fundingOutputPolicies)

            // As users can enter newOp screen with 0 balance, we need to check for amount == 0
            // because of our rule (if balance == amount then useAllFunds = true)
            if (!payReq.takeFeeFromAmount || payReq.amount!!.isZero) {
                // Great amount is fixed! The user selected a specific amount

                // Compute off-chain fees
                val params = payReq.swap.getParamsForAmount(originalAmountInSatoshis, false)
                val outputAmountInSatoshis = params.outputAmountFor(originalAmountInSatoshis)

                val swap = payReq.swap.withParams(params, outputAmountInSatoshis)
                return analyzeFixedAmountSubmarineSwap(payReq.withSwap(swap))

            } else {

                // Let's play in Extra Hard mode! Analyze AmountLess Invoice with TFFA
                return analyzeTffaAmountLessInvoiceSwap()
            }
        }
    }

    private fun analyzeTffaAmountLessInvoiceSwap(): PaymentAnalysis {
        checkNotNull(payReq.swap)
        checkNotNull(payReq.swap.bestRouteFees)
        checkNotNull(payReq.swap.fundingOutputPolicies)
        check(payReq.takeFeeFromAmount)

        var (feeInSats, offchainAmountInSats, params, feeRate) = computeTffaParamsFor(payReq.swap, 0)

        // If we don't qualify for 0-conf, redo the computation with 1-conf
        if (params.confirmationsNeeded == 1) {

            val (newFee, newOffChainAmount, newParams, newFeeRate) = computeTffaParamsFor(payReq.swap, 1)
            feeInSats = newFee
            offchainAmountInSats = newOffChainAmount
            params = newParams
            feeRate = newFeeRate
        }

        Preconditions.checkState(params.debtType != DebtType.LEND)

        // Subtract the on and off-chain fees
        val updatedAmountInSats = offchainAmountInSats
        // This assumes that debt amount is either 0 or positive and a collect
        val outputAmountInSatoshis =
            offchainAmountInSats + params.offchainFeeInSats + params.debtAmountInSats

        if (updatedAmountInSats < 0) {
            // AKA analizeCannotPay + the data (estimates) we know so far
            return createAnalysis(
                amountInSatoshis = originalAmountInSatoshis,
                feeInSatoshis = feeInSats,
                totalInSatoshis = null,
                outputAmountInSatoshis = outputAmountInSatoshis,
                swapFees = SubmarineSwapFees(
                    params.routingFeeInSats,
                    params.sweepFeeInSats
                ),
                canPayWithoutFee = false,
                canPayWithSelectedFee = false,
                canPayWithMinimumFee = false
            )
        }
        val updatedPayReq = payReq
                .withFeeRate(feeRate)
                .withSwap(payReq.swap.withParams(params, outputAmountInSatoshis))

        return createAnalysis(
            updatedPayReq = updatedPayReq,
            amountInSatoshis = updatedAmountInSats,
            feeInSatoshis = feeInSats,
            totalInSatoshis = originalAmountInSatoshis,
            outputAmountInSatoshis = outputAmountInSatoshis,
            swapFees = SubmarineSwapFees(
                params.routingFeeInSats,
                params.sweepFeeInSats
            ),
            canPayWithoutFee = true,
            // For swaps, fee is fixed. We choose a sensible conf target and that's it.
            // There's no changing the selectedFee, and that's why minimumFee doesn't really
            // make sense for swaps. It'll always be equal to selectedFee.
            canPayWithMinimumFee = true,
            canPayWithSelectedFee = true
        )
    }

    private fun computeTffaParamsFor(swap: SubmarineSwap, confTarget: Int): SwapAnalysisParams {

        check(payReq.takeFeeFromAmount)

        val feeRate = payCtx.feeWindow.getSwapFeeRate(confTarget)
        val feeCalculator = FeeCalculator(feeRate, nextTransactionSize)

        // Compute tha on-chain fee. As its TFFA, we want to calculate the fee for the total balance
        val spentAmount =
            totalBalanceInSatoshis + (swap.fundingOutputPolicies?.potentialCollectInSat ?: 0)
        val feeInSatoshis = feeCalculator.calculateForCollect(
            spentAmount, takeFeeFromAmount = true
        )
        val outputAmountInSatoshis = spentAmount - feeInSatoshis

        // Get a first approximation (by excess) of the off-chain fee which we will later refine
        var params = swap.getParamsForAmount(outputAmountInSatoshis, true)
        Preconditions.checkState(params.debtType != DebtType.LEND)

        // Find the point at which the off-chain amount (displayed to the user) plus the off-chain
        // fee equals our output amount
        var offchainAmountInSatoshis =
                outputAmountInSatoshis - params.offchainFeeInSats - params.debtAmountInSats

        while (true) {
            params = swap.getParamsForAmount(offchainAmountInSatoshis, true)

            // TODO the > scenario is tricky (will break). Let's throw non fatal, track occurrences
            if (offchainAmountInSatoshis + params.debtAmountInSats
                    + params.offchainFeeInSats >= outputAmountInSatoshis) {
                break
            }

            offchainAmountInSatoshis += 1
        }

        return SwapAnalysisParams(feeInSatoshis, offchainAmountInSatoshis, params, feeRate)
    }

    private fun analyzeFixedAmountSubmarineSwap(payReq: PaymentRequest): PaymentAnalysis {
        checkNotNull(payReq.swap)

        val newPayReq = payReq.withFeeRate(payCtx.feeWindow.getFeeRate(payReq.swap))

        return if (payReq.swap.isLend()) {
            analyzeLendSubmarineSwap(newPayReq)

        } else if (payReq.swap.isCollect()) {
            analyzeCollectSubmarineSwap(newPayReq)

        } else {
            analyzeNonDebtSubmarineSwap(newPayReq)
        }
    }

    private fun analyzeLendSubmarineSwap(payReq: PaymentRequest): PaymentAnalysis {
        checkNotNull(payReq.swap)
        checkNotNull(payReq.swap.fees)

        // When lending, the outputAmount and sweepFee fields given by Swapper must be ignored:
        val amountInSatoshis = originalAmountInSatoshis
        val totalInSatoshis = amountInSatoshis + payReq.swap.fees.lightningInSats
        val swapFees = SubmarineSwapFees(lightningInSats = payReq.swap.fees.lightningInSats)

        val canPayLightningFee = (totalInSatoshis <= totalBalanceInSatoshis)

        return createAnalysis(
            updatedPayReq = payReq,
            amountInSatoshis = amountInSatoshis,
            feeInSatoshis = 0, // no actual transaction
            totalInSatoshis = if (canPayLightningFee) totalInSatoshis else null,
            outputAmountInSatoshis = payReq.swap.fundingOutput.outputAmountInSatoshis,
            swapFees = swapFees,
            canPayWithoutFee = (amountInSatoshis <= totalBalanceInSatoshis) && amountInSatoshis > 0,
            // For swaps, fee is fixed. We choose a sensible conf target and that's it. There's no
            // changing the selectedFee, and that's why minimumFee doesn't really make
            // sense for swaps. It'll always be equal to selectedFee.
            canPayWithMinimumFee = canPayLightningFee,
            canPayWithSelectedFee = canPayLightningFee
        )
    }

    private fun analyzeCollectSubmarineSwap(payReq: PaymentRequest): PaymentAnalysis {
        checkNotNull(payReq.swap)
        checkNotNull(payReq.swap.fees)
        checkNotNull(payReq.swap.fundingOutput.outputAmountInSatoshis)
        checkNotNull(payReq.swap.fundingOutput.debtAmountInSatoshis)
        checkNotNull(payReq.feeInSatoshisPerByte)

        val outputAmountInSatoshis = payReq.swap.fundingOutput.outputAmountInSatoshis
        val collectAmountInSatoshis = payReq.swap.fundingOutput.debtAmountInSatoshis
        val amountInSatoshis = outputAmountInSatoshis - collectAmountInSatoshis

        val expectedAmountInSat = originalAmountInSatoshis +
            payReq.swap.fees.lightningInSats +
            payReq.swap.fees.sweepInSats

        check(amountInSatoshis == expectedAmountInSat) {
            "Check failed." +
                "(amountInSatoshis=$amountInSatoshis;" +
                "originalAmountInSatoshis=$originalAmountInSatoshis;" +
                "lightningInSats=${payReq.swap.fees.lightningInSats};" +
                "sweepInSats=${payReq.swap.fees.sweepInSats})"
        }

        // For COLLECT swaps, outputAmountInSatoshis includes collectAmount so check must be
        // performed against utxoBalance
        if (outputAmountInSatoshis > totalUtxoBalanceInSatoshis) {
            // Unlike other cases, this can happen because we calculate fee for the total output
            // amount (which includes the sweep fee) and not the original payment amount. So, our
            // caller has not verified this can be payed.
            return analyzeCannotPay(payReq)
        }

        val feeInSatoshis = FeeCalculator(payReq.feeInSatoshisPerByte, nextTransactionSize)
                .calculateForCollect(outputAmountInSatoshis)
        val totalInSatoshis = outputAmountInSatoshis + feeInSatoshis

        val totalForDisplayInSatoshis = amountInSatoshis + feeInSatoshis

        // We need to ensure we can spend on chain and that we have enough UI visible balance too
        // That is, the collect doesn't make us spend more than we really can and the amount + fee
        // doesn't default any debt.
        val canPay = totalInSatoshis <= totalUtxoBalanceInSatoshis
            && totalForDisplayInSatoshis <= totalBalanceInSatoshis

        return createAnalysis(
            updatedPayReq = payReq,
            amountInSatoshis = originalAmountInSatoshis,
            feeInSatoshis = feeInSatoshis,
            totalInSatoshis = totalForDisplayInSatoshis,
            outputAmountInSatoshis = outputAmountInSatoshis,
            swapFees = payReq.swap.fees,
            canPayWithoutFee = true,
            // For swaps, fee is fixed. We choose a sensible conf target and that's it. There's no
            // changing the selectedFee, and that's why minimumFee doesn't really make
            // sense for swaps. It'll always be equal to selectedFee.
            canPayWithMinimumFee = canPay,
            canPayWithSelectedFee = canPay
        )
    }

    private fun analyzeNonDebtSubmarineSwap(payReq: PaymentRequest): PaymentAnalysis {
        checkNotNull(payReq.swap)
        checkNotNull(payReq.swap.fees)
        checkNotNull(payReq.swap.fundingOutput.outputAmountInSatoshis)
        checkNotNull(payReq.swap.fundingOutput.debtAmountInSatoshis)
        checkNotNull(payReq.feeInSatoshisPerByte)

        val outputAmountInSatoshis = payReq.swap.fundingOutput.outputAmountInSatoshis

        val expectedAmountInSat = originalAmountInSatoshis +
            payReq.swap.fees.lightningInSats +
            payReq.swap.fees.sweepInSats

        check(outputAmountInSatoshis == expectedAmountInSat) {
            "Check failed." +
                "(outputAmountInSatoshis=$outputAmountInSatoshis;" +
                "originalAmountInSatoshis=$originalAmountInSatoshis;" +
                "lightningInSats=${payReq.swap.fees.lightningInSats};" +
                "sweepInSats=${payReq.swap.fees.sweepInSats})"
        }

        if (outputAmountInSatoshis > totalBalanceInSatoshis) {
            // Unlike other cases, this can happen because we calculate fee for the total output
            // amount (which includes the sweep fee) and not the original payment amount. So, our
            // caller has not verified this can be payed.
            return analyzeCannotPay(payReq)
        }

        val feeInSatoshis = FeeCalculator(payReq.feeInSatoshisPerByte, nextTransactionSize)
                .calculateForCollect(outputAmountInSatoshis)
        val totalInSatoshis = outputAmountInSatoshis + feeInSatoshis

        return createAnalysis(
            updatedPayReq = payReq,
            amountInSatoshis = originalAmountInSatoshis,
            feeInSatoshis = feeInSatoshis,
            totalInSatoshis = totalInSatoshis,
            outputAmountInSatoshis = outputAmountInSatoshis,
            swapFees = payReq.swap.fees,
            canPayWithoutFee = true,
            // For swaps, fee is fixed. We choose a sensible conf target and that's it. There's no
            // changing the selectedFee, and that's why minimumFee doesn't really make
            // sense for swaps. It'll always be equal to selectedFee.
            canPayWithMinimumFee = (totalInSatoshis <= totalBalanceInSatoshis),
            canPayWithSelectedFee = (totalInSatoshis <= totalBalanceInSatoshis)
        )
    }

    private fun createAnalysis(updatedPayReq: PaymentRequest? = null,
                               amountInSatoshis: Long,
                               feeInSatoshis: Long?,
                               totalInSatoshis: Long?,
                               outputAmountInSatoshis: Long? = null, // swap only
                               swapFees: SubmarineSwapFees? = null, // same
                               canPayWithoutFee: Boolean,
                               canPayWithSelectedFee: Boolean,
                               canPayWithMinimumFee: Boolean): PaymentAnalysis {

        val amount = convertToBitcoinAmount(amountInSatoshis)
        val sweepFee = convertToNullableBitcoinAmount(swapFees?.sweepInSats)
        val lightningFee = convertToNullableBitcoinAmount(swapFees?.lightningInSats)
        val fee = convertToNullableBitcoinAmount(feeInSatoshis)


        // Ok, so here's how it goes. Sometimes we don't care to calculate the total, as the
        // payment is invalid/can't be paid. (Most of) Those times, totalInSatoshis is null, so
        // we're fine leaving total null (and avoiding the precondition check).
        var total: BitcoinAmount? = null

        if (totalInSatoshis != null) {

            // Avoiding precondition check, and keep returning same value as before, for TFFA edge
            // case where we return a non-null total (e.g total == amount) when
            // canPayWithSelectedFee is false.
            // TODO: fix this edge case
            if (payReq.takeFeeFromAmount && !canPayWithSelectedFee) {
                total = convertToBitcoinAmount(totalInSatoshis)

            } else {

                // Doing math with converted amounts to avoid showing rounding errors in UI.
                total = amount
                    .add(sweepFee)
                    .add(lightningFee)
                    .add(fee)

                check(totalInSatoshis == total.inSatoshis) {
                    "Check failed." +
                        "(TotalInSatoshis=$totalInSatoshis;" +
                        "total.inSatoshis=${total.inSatoshis};" +
                        "amountInSatoshis=${amountInSatoshis};" +
                        "feeInSatoshis=${feeInSatoshis};" +
                        "sweepFee=${sweepFee?.inSatoshis};" +
                        "lightningFee=${lightningFee?.inSatoshis};" +
                        "rate=${payCtx.exchangeRateWindow.windowHid})"
                }
            }
        }

        return PaymentAnalysis(
            updatedPayReq ?: payReq,
            totalBalance = convertToBitcoinAmount(totalBalanceInSatoshis),

            amount = amount,
            outputAmount = convertToBitcoinAmount(outputAmountInSatoshis ?: amountInSatoshis),
            sweepFee = sweepFee,
            lightningFee = lightningFee,
            fee = fee,
            total = total,
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