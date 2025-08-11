package io.muun.apollo.domain.model

import io.muun.common.api.SubmarineSwapBestRouteFeesJson

/**
 * Transient. Not stored locally.
 */
class SubmarineSwapBestRouteFees(
    val maxCapacityInSat: Long,
    val proportionalMillionth: Long,
    val baseInSat: Long
) {

    companion object {
        fun fromJson(json: SubmarineSwapBestRouteFeesJson) =
            SubmarineSwapBestRouteFees(
                json.maxCapacityInSat,
                json.proportionalMillionth,
                json.baseInSat
            )
    }

    /**
     * Adapt apollo's (java) model to libwallet's (go).
     */
    fun toLibwallet(): newop.BestRouteFees {
        val libwalletBestRouteFees = newop.BestRouteFees()
        libwalletBestRouteFees.maxCapacity = maxCapacityInSat
        libwalletBestRouteFees.feeProportionalMillionth = proportionalMillionth
        libwalletBestRouteFees.feeBase = baseInSat
        return libwalletBestRouteFees
    }
}
