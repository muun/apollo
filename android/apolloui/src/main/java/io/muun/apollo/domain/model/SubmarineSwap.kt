package io.muun.apollo.domain.model

import io.muun.apollo.data.serialization.dates.ApolloZonedDateTime
import io.muun.apollo.domain.model.base.HoustonUuidModel
import io.muun.common.api.SubmarineSwapJson
import io.muun.common.model.DebtType
import newop.SwapInfo
import org.threeten.bp.ZonedDateTime

/**
 * Properties tagged as "Transient" are not store locally. They are needed at a specific time,
 * received from Houston, used for processing and then discarded. E.g For estimating fees for
 * AmountLess Invoices.
 */
class SubmarineSwap(
    id: Long?,
    houstonUuid: String,
    val invoice: String,
    val receiver: SubmarineSwapReceiver,
    val fundingOutput: SubmarineSwapFundingOutput,
    val fees: SubmarineSwapFees?,
    val expiresAt: ZonedDateTime,
    val payedAt: ZonedDateTime?,                                            // may not be payed yet
    private val preimageInHex: String?,
    private val bestRouteFees: List<SubmarineSwapBestRouteFees>? = null,            // Transient
    private val fundingOutputPolicies: SubmarineSwapFundingOutputPolicies? = null,  // Transient
    val maxAlternativeTransactions: Int? = null,                                    // Transient
) : HoustonUuidModel(id, houstonUuid) {

    companion object {
        fun fromJson(swap: SubmarineSwapJson?): SubmarineSwap? {
            return if (swap == null) {
                null

            } else SubmarineSwap(
                null,
                swap.swapUuid,
                swap.invoice,
                SubmarineSwapReceiver.fromJson(swap.receiver),
                SubmarineSwapFundingOutput.fromJson(swap.fundingOutput),
                swap.fees?.let(SubmarineSwapFees::fromJson),
                ApolloZonedDateTime.fromMuunZonedDateTime(swap.expiresAt)!!.dateTime,
                ApolloZonedDateTime.fromMuunZonedDateTime(swap.payedAt)?.dateTime,
                swap.preimageInHex,
                swap.bestRouteFees?.map(SubmarineSwapBestRouteFees::fromJson),
                swap.fundingOutputPolicies?.let(SubmarineSwapFundingOutputPolicies::fromJson),
                swap.maxAlternativeTransactionCount
            )
        }
    }

    val preimage
        get() = if (this.preimageInHex != null) {
            Preimage.fromHex(this.preimageInHex)
        } else {
            null
        }

    val isLend: Boolean
        get() = fundingOutput.debtType == DebtType.LEND

    val isCollect: Boolean
        get() = fundingOutput.debtType == DebtType.COLLECT

    fun toJson() =
        SubmarineSwapJson(
            houstonUuid,
            invoice,
            receiver.toJson(),
            fundingOutput.toJson(),
            fees?.toJson(),
            ApolloZonedDateTime.of(expiresAt),
            ApolloZonedDateTime.fromNullable(payedAt),
            preimageInHex
        )

    /**
     * Return a cloned SubmarineSwap adding certain params calculated after specifying amount.
     * Used for AmountLess Invoice swaps.
     */
    fun withAmountLessInfo(swapInfo: SwapInfo): SubmarineSwap {
        val swapFees = swapInfo.swapFees
        return SubmarineSwap(
            id,
            houstonUuid,
            invoice,
            receiver,
            fundingOutput.withSwapInfo(swapInfo),
            SubmarineSwapFees(swapFees.routingFeeInSat, swapFees.outputPaddingInSat),
            expiresAt,
            payedAt,
            preimageInHex,
            maxAlternativeTransactions = maxAlternativeTransactions
        )
    }

    private fun outputPaddingInSat(): Long? =
        fees?.outputPaddingInSat(fundingOutput.debtType!!)

    fun totalFeesInSat(): Long? =
        fees?.totalInSat(fundingOutput.debtType!!)

    /**
     * Adapt apollo's (java) model to libwallet's (go).
     */
    fun toLibwallet(): newop.SubmarineSwap {
        val libwalletSwap = newop.SubmarineSwap()
        libwalletSwap.receiver = receiver.toLibwallet()

        fees?.let {
            val swapFees = newop.SwapFees()
            swapFees.confirmationsNeeded = (fundingOutput.confirmationsNeeded ?: 0).toLong()
            swapFees.debtAmountInSat = fundingOutput.debtAmountInSatoshis ?: 0
            swapFees.debtType = fundingOutput.debtType?.name ?: ""
            swapFees.outputAmountInSat = fundingOutput.outputAmountInSatoshis ?: 0
            swapFees.outputPaddingInSat = outputPaddingInSat()!!
            swapFees.routingFeeInSat = fees.lightningInSats
            libwalletSwap.fees = swapFees
        }

        libwalletSwap.fundingOutputPolicies = fundingOutputPolicies?.toLibwallet()

        bestRouteFees?.iterator()?.forEach { bestRouteFee ->
            libwalletSwap.addBestRouteFees(bestRouteFee.toLibwallet())
        }

        return libwalletSwap
    }
}
