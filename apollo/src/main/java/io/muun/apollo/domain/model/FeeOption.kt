package io.muun.apollo.domain.model

import io.muun.apollo.domain.utils.FeeCalculator


/**
 * A recommended fee rate the user can pick, with related information.
 */
data class FeeOption(
    /** The fee rate, in satoshis per byte. */
    val satoshisPerByte: Double,

    /** The expected confirmation target for this fee rate, in blocks. */
    val confirmationTarget: Int,

    /** The minimum estimated confirmation wait in milliseconds. */
    val minTimeMs: Long,

    /** The maximum estimated confirmation wait in milliseconds. */
    val maxTimeMs: Long,

    /** The fee calculator for this fee rate. */
    val feeCalculator: FeeCalculator
) {

    fun withMaxTimeMs(newMaxTimeMs: Long) =
        copy(maxTimeMs = newMaxTimeMs)
}
