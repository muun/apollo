package io.muun.apollo.domain.model

import io.muun.apollo.data.serialization.dates.ApolloZonedDateTime
import io.muun.apollo.domain.model.base.HoustonUuidModel
import io.muun.common.api.SubmarineSwapJson
import io.muun.common.model.DebtType
import libwallet.BestRouteFees
import libwallet.BestRouteFeesList
import libwallet.FundingOutputPolicies
import libwallet.Libwallet
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
    val bestRouteFees: List<SubmarineSwapBestRouteFees>? = null,            // Transient
    val fundingOutputPolicies: SubmarineSwapFundingOutputPolicies? = null,  // Transient
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
                swap.fundingOutputPolicies?.let(SubmarineSwapFundingOutputPolicies::fromJson)
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

    @Deprecated("Should be remove with old NewOp Presenter and PaymentAnalyzer", ReplaceWith(""))
    fun getParamsForAmount(amountInSats: Long, takeFeeFromAmount: Boolean): SubmarineSwapExecutionParameters {

        checkNotNull(bestRouteFees)
        checkNotNull(fundingOutputPolicies)

        val bestRouteFeesList = BestRouteFeesList()

        bestRouteFees.forEach { route ->

            val item = BestRouteFees()
            item.maxCapacity = route.maxCapacityInSat
            item.feeProportionalMillionth = route.proportionalMillionth
            item.feeBase = route.baseInSat

            bestRouteFeesList.add(item)
        }

        val policies = FundingOutputPolicies()
        policies.maximumDebt = if (takeFeeFromAmount) {
            0
        } else {
            fundingOutputPolicies.maximumDebtInSat
        }
        policies.potentialCollect = fundingOutputPolicies.potentialCollectInSat
        policies.maxAmountFor0Conf = fundingOutputPolicies.maxAmountInSatFor0Conf

        val fees = Libwallet.computeSwapFees(amountInSats, bestRouteFeesList, policies)!!

        return SubmarineSwapExecutionParameters(
            fees.sweepFee,
            fees.routingFee,
            DebtType.valueOf(fees.debtType),
            fees.debtAmount,
            fees.confirmationsNeeded.toInt()
        )
    }

    /**
     * Use this with caution. So far, we use it ONLY for special analysis after INSUFFICIENT_FUNDS.
     */
    @Deprecated("Should be remove with old NewOp Presenter and PaymentAnalyzer", ReplaceWith(""))
    fun withAmount(newAmountInSat: Long): SubmarineSwap =
        SubmarineSwap(
            id,
            houstonUuid,
            invoice,
            receiver,
            fundingOutput.withOutputAmount(newAmountInSat + fees!!.total),
            fees,
            expiresAt,
            payedAt,
            preimageInHex
        )

    /**
     * Return a cloned SubmarineSwap adding certain SubmarineSwapExecutionParameters.
     * Used for AmountLess Invoice swaps.
     */
    @Deprecated("Should be remove with old NewOp Presenter and PaymentAnalyzer", ReplaceWith(""))
    fun withParams(params: SubmarineSwapExecutionParameters, outputAmountInSats: Long) =
        SubmarineSwap(
            id,
            houstonUuid,
            invoice,
            receiver,
            fundingOutput.withSwapParams(params, outputAmountInSats),
            SubmarineSwapFees(params.routingFeeInSats, params.sweepFeeInSats),
            expiresAt,
            payedAt,
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
            preimageInHex
        )
    }

    fun outputPaddingInSat(): Long?  =
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

        bestRouteFees?.forEach { bestRouteFee ->
            libwalletSwap.addBestRouteFees(bestRouteFee.toLibwallet())
        }

        return libwalletSwap
    }
}
