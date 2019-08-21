package io.muun.apollo.domain.model

import io.muun.common.api.SubmarineSwapFeesJson


class SubmarineSwapFees(
    val lightningInSats: Long = 0,
    val sweepInSats: Long = 0,
    val channelOpenInSats: Long = 0,
    val channelCloseInSats: Long = 0
) {

    val total =
        lightningInSats + sweepInSats + channelOpenInSats + channelCloseInSats

    fun toJson() =
        SubmarineSwapFeesJson(
            lightningInSats,
            sweepInSats,
            channelOpenInSats,
            channelCloseInSats
        )

    companion object {
        fun fromJson(json: SubmarineSwapFeesJson) =
            SubmarineSwapFees(
                json.lightningInSats,
                json.sweepInSats,
                json.channelOpenInSats,
                json.channelCloseInSats
            )
    }
}
