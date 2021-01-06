package io.muun.apollo.domain.model

import io.muun.common.model.DebtType

class SubmarineSwapExecutionParameters(
    val sweepFeeInSats: Long,
    val routingFeeInSats: Long,
    val debtType: DebtType,
    val debtAmountInSats: Long,
    val confirmationsNeeded: Int
) {

    val offchainFeeInSats = routingFeeInSats + sweepFeeInSats

    fun outputAmountFor(amountInSats: Long): Long {
        var outputAmountInSats = amountInSats + offchainFeeInSats

        // We have to add the COLLECTABLE_AMOUNT to the outputAmountInSatoshis in Collect swaps
        if (debtType == DebtType.COLLECT)  {
            outputAmountInSats += debtAmountInSats
        }

        return outputAmountInSats
    }
}

data class SwapAnalysisParams(
    val feeInSatoshis: Long,
    val offchainAmountInSatoshis: Long,
    val params: SubmarineSwapExecutionParameters,
    val feeRate: Double
)