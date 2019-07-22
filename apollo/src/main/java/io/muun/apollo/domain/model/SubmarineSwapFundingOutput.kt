package io.muun.apollo.domain.model

import io.muun.common.api.SubmarineSwapFundingOutputJson
import io.muun.common.crypto.hd.MuunAddress

class SubmarineSwapFundingOutput(
        val outputAddress: String,
        val outputAmountInSatoshis: Long,
        val confirmationsNeeded: Int,
        val userLockTime: Int,
        val userRefundAddress: MuunAddress,
        val serverPaymentHashInHex: String,
        val serverPublicKeyInHex: String) {

    fun toJson() =
            SubmarineSwapFundingOutputJson(
                    outputAddress,
                    outputAmountInSatoshis,
                    confirmationsNeeded,
                    userLockTime,
                    userRefundAddress.toJson(),
                    serverPaymentHashInHex,
                    serverPublicKeyInHex
            )

    companion object {

        fun fromJson(output: SubmarineSwapFundingOutputJson): SubmarineSwapFundingOutput {
            return SubmarineSwapFundingOutput(
                    output.outputAddress,
                    output.outputAmountInSatoshis,
                    output.confirmationsNeeded,
                    output.userLockTime,
                    MuunAddress.fromJson(output.userRefundAddress),
                    output.serverPaymentHashInHex,
                    output.serverPublicKeyInHex
            )
        }
    }
}
