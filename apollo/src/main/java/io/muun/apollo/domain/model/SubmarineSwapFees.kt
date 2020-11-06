package io.muun.apollo.domain.model

import io.muun.common.api.SubmarineSwapFeesJson


class SubmarineSwapFees(val lightningInSats: Long = 0, val sweepInSats: Long = 0) {

    val total = lightningInSats + sweepInSats

    fun toJson() =
        SubmarineSwapFeesJson(lightningInSats, sweepInSats)

    companion object {
        fun fromJson(json: SubmarineSwapFeesJson) =
            SubmarineSwapFees(json.lightningInSats, json.sweepInSats)
    }
}
