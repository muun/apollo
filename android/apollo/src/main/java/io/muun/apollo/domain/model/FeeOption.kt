package io.muun.apollo.domain.model

import io.muun.apollo.domain.utils.FeeCalculator
import io.muun.common.bitcoinj.BlockHelpers
import kotlin.math.max


/**
 * A recommended fee rate the user can pick, with related information.
 */
data class FeeOption(
    /** The fee rate, in satoshis per byte. */
    val satoshisPerByte: Double,

    /** The expected confirmation target for this fee rate, in blocks. */
    val confirmationTarget: Int,

    /** The fee calculator for this fee rate. */
    val feeCalculator: FeeCalculator
) {

    companion object {
        private const val CONF_CERTAINTY = 0.75
    }

    /** The minimum estimated confirmation wait in milliseconds. */
    val minTimeMs by lazy {
        estimateTimeMs(max(confirmationTarget - 2, 1))
    }

    /** The maximum estimated confirmation wait in milliseconds. */
    val maxTimeMs by lazy {
        estimateTimeMs(confirmationTarget)
    }

    private fun estimateTimeMs(numBlocks: Int) =
        BlockHelpers
            .timeInSecsForBlocksWithCertainty(numBlocks, CONF_CERTAINTY).toLong() * 1000
}
