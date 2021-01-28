package io.muun.apollo.domain.model

import io.muun.common.api.SubmarineSwapFundingOutputPoliciesJson

/**
 * Transient. Not stored locally.
 */
class SubmarineSwapFundingOutputPolicies(
    val maximumDebtInSat: Long,
    val potentialCollectInSat: Long,
    val maxAmountInSatFor0Conf: Long
) {

    companion object {
        fun fromJson(json: SubmarineSwapFundingOutputPoliciesJson) =
            SubmarineSwapFundingOutputPolicies(
                json.maximumDebtInSat,
                json.potentialCollectInSat,
                json.maxAmountInSatFor0Conf
            )
    }
}
