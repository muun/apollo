package io.muun.apollo.domain.model

import io.muun.common.api.SubmarineSwapFeesJson
import io.muun.common.model.DebtType


class SubmarineSwapFees(val lightningInSats: Long = 0, private val outputPaddingInSats: Long = 0) {

    /**
     * LEND swaps have no associated on-chain tx, so sweepFee/outputPadding does not apply. This
     * is enforced here as our backend (namely Swapper), was designed so that Swaps were categorized
     * as LEND in an "opt-in" way. That is to say, Swapper signals a swap CAN BE a LEND swap and
     * clients may or may not follow. For that, swaps come with sweepFee/outputPadding in case the
     * client decides to do a proper swap for sub-dust amounts.
     */
    fun outputPaddingInSat(debtType: DebtType): Long =
        if (debtType == DebtType.LEND) 0 else outputPaddingInSats

    /**
     * Return the sum of routing/lightningFee + outputPadding/sweepFee.
     * See outputPaddingInSat.
     */
    fun totalInSat(debtType: DebtType): Long =
        lightningInSats + outputPaddingInSat(debtType)

    fun toJson() =
        SubmarineSwapFeesJson(lightningInSats, outputPaddingInSats)

    companion object {
        fun fromJson(json: SubmarineSwapFeesJson) =
            SubmarineSwapFees(json.lightningInSats, json.sweepInSats)
    }
}
