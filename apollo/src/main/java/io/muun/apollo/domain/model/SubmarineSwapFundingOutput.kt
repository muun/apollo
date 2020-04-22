package io.muun.apollo.domain.model

import io.muun.common.Supports
import io.muun.common.api.SubmarineSwapFundingOutputJson
import io.muun.common.crypto.hd.MuunAddress
import io.muun.common.crypto.hd.PublicKey
import io.muun.common.crypto.schemes.TransactionSchemeSubmarineSwap
import io.muun.common.model.DebtType
import io.muun.common.utils.Deprecated

class SubmarineSwapFundingOutput constructor(
    val outputAddress: String,
    val outputAmountInSatoshis: Long,
    val debtType: DebtType,
    val debtAmountInSatoshis: Long,
    val confirmationsNeeded: Int,
    val userLockTime: Int?,                 // for swaps v2 is null until funding tx confirmation
    @Deprecated(
        atApolloVersion = Supports.SubmarineSwapsV2.APOLLO,
        atVersion = Supports.SubmarineSwapsV2.FALCON
    )
    val userRefundAddress: MuunAddress,
    val serverPaymentHashInHex: String,
    val serverPublicKeyInHex: String,
    var scriptVersion: Int = TransactionSchemeSubmarineSwap.ADDRESS_VERSION,
    var expirationInBlocks: Int? = null,    // for swaps v2 not nullable
    var userPublicKey: PublicKey? = null,   // for swaps v2 not nullable
    var muunPublicKey: PublicKey? = null    // for swaps v2 not nullable)
) {

    fun toJson() =
        SubmarineSwapFundingOutputJson(
            outputAddress,
            outputAmountInSatoshis,
            confirmationsNeeded,
            userLockTime,
            expirationInBlocks,
            userRefundAddress.toJson(),
            userPublicKey?.toJson(),
            muunPublicKey?.toJson(),
            serverPaymentHashInHex,
            serverPublicKeyInHex,
            scriptVersion,
            debtType,
            debtAmountInSatoshis
        )

    companion object {

        fun fromJson(output: SubmarineSwapFundingOutputJson): SubmarineSwapFundingOutput {
            return SubmarineSwapFundingOutput(
                output.outputAddress,
                output.outputAmountInSatoshis,
                output.debtType,
                output.debtAmountInSats ?: 0,
                output.confirmationsNeeded,
                output.userLockTime,
                MuunAddress.fromJson(output.userRefundAddress),
                output.serverPaymentHashInHex,
                output.serverPublicKeyInHex,
                output.scriptVersion,
                output.expirationInBlocks,
                PublicKey.fromJson(output.userPublicKey),
                PublicKey.fromJson(output.muunPublicKey)
            )
        }
    }
}
