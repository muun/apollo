package io.muun.apollo.domain.model

import io.muun.apollo.domain.model.feebump.FeeBumpFunctions
import org.threeten.bp.ZonedDateTime

data class RealTimeFees(
    // Each fee bump functions is codified as a base64 string.
    val feeBumpFunctions: FeeBumpFunctions,
    val feeWindow: FeeWindow,
    val minMempoolFeeRateInSatPerVbyte: Double,
    val minFeeRateIncrementToReplaceByFeeInSatPerVbyte: Double,
    val computedAt: ZonedDateTime
)