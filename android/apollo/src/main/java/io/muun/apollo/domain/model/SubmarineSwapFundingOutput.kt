package io.muun.apollo.domain.model

import io.muun.common.Supports
import io.muun.common.api.SubmarineSwapFundingOutputJson
import io.muun.common.crypto.hd.MuunAddress
import io.muun.common.crypto.hd.PublicKey
import io.muun.common.crypto.schemes.TransactionSchemeSubmarineSwap
import io.muun.common.model.DebtType
import io.muun.common.utils.Deprecated

data class SubmarineSwapFundingOutput(
    val outputAddress: String,
    val outputAmountInSatoshis: Long?,
    val debtType: DebtType?,
    val debtAmountInSatoshis: Long?,
    val confirmationsNeeded: Int?,
    val userLockTime: Int?,                 // for swaps v2 is null until funding tx confirmation
    @Deprecated(
        atApolloVersion = Supports.SubmarineSwapsV2.APOLLO,
        atVersion = Supports.SubmarineSwapsV2.FALCON
    )
    val userRefundAddress: MuunAddress,
    private val serverPaymentHashInHex: String,
    val serverPublicKeyInHex: String,
    val scriptVersion: Int,
    val expirationInBlocks: Int? = null,    // for swaps v2 not nullable
    val userPublicKey: PublicKey? = null,   // for swaps v2 not nullable
    val muunPublicKey: PublicKey? = null    // for swaps v2 not nullable)
) {

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

    val paymentHash: Sha256Hash
        get() = Sha256Hash.fromHex(serverPaymentHashInHex)

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

    /**
     * Use this with caution. So far, we use it ONLY for special analysis after INSUFFICIENT_FUNDS.
     */
    fun withOutputAmount(newAmountInSat: Long): SubmarineSwapFundingOutput =
        copy(outputAmountInSatoshis = newAmountInSat + debtAmountInSatoshis!!)

    /**
     * Return a cloned SubmarineSwapFundingOutput adding certain SubmarineSwapExecutionParameters.
     * Used for AmountLess Invoice swaps.
     */
    fun withSwapParams(params: SubmarineSwapExecutionParameters, outputAmountInSats: Long) =
        copy(
            outputAmountInSatoshis = outputAmountInSats,
            debtType = params.debtType,
            debtAmountInSatoshis =  params.debtAmountInSats,
            confirmationsNeeded = params.confirmationsNeeded
        )
}
