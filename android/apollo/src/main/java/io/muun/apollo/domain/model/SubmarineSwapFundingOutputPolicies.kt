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

    /**
     * Adapt apollo's (java) model to libwallet's (go).
     */
    fun toLibwallet(): newop.FundingOutputPolicies {
        val libwalletFundingOutputPolicies = newop.FundingOutputPolicies()
        libwalletFundingOutputPolicies.maximumDebtInSat = maximumDebtInSat
        libwalletFundingOutputPolicies.potentialCollectInSat = potentialCollectInSat
        libwalletFundingOutputPolicies.maxAmountInSatFor0Conf = maxAmountInSatFor0Conf
        return libwalletFundingOutputPolicies
    }
}
