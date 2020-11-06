package io.muun.apollo.domain.model

import io.muun.apollo.data.serialization.dates.ApolloZonedDateTime
import io.muun.apollo.domain.model.base.HoustonUuidModel
import io.muun.common.api.SubmarineSwapJson
import io.muun.common.model.DebtType
import org.threeten.bp.ZonedDateTime

class SubmarineSwap (id: Long?,
                    houstonUuid: String,
                    val invoice: String,
                    val receiver: SubmarineSwapReceiver,
                    val fundingOutput: SubmarineSwapFundingOutput,
                    val fees: SubmarineSwapFees,
                    val expiresAt: ZonedDateTime,
                    var payedAt: ZonedDateTime?,         // may not be payed yet
                    var preimageInHex: String?) : HoustonUuidModel(id, houstonUuid) {

    fun isLend() =
        fundingOutput.debtType == DebtType.LEND

    fun isCollect() =
        fundingOutput.debtType == DebtType.COLLECT

    fun toJson() =
            SubmarineSwapJson(
                    houstonUuid,
                    invoice,
                    receiver.toJson(),
                    fundingOutput.toJson(),
                    fees.toJson(),
                    ApolloZonedDateTime.of(expiresAt),
                    ApolloZonedDateTime.fromNullable(payedAt),
                    preimageInHex
            )

    /**
     * Use this with caution. So far, we use it ONLY for special analysis after INSUFFICIENT_FUNDS.
     */
    fun withAmount(newAmountInSat: Long): SubmarineSwap =
        SubmarineSwap(
            id,
            houstonUuid,
            invoice,
            receiver,
            fundingOutput.withOutputAmount(newAmountInSat + fees.total),
            fees,
            expiresAt,
            payedAt,
            preimageInHex
        )

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
                    // FIXME: When implementing amount-less invoices turns this optional
                    SubmarineSwapFees.fromJson(swap.fees!!),
                    ApolloZonedDateTime.fromMuunZonedDateTime(swap.expiresAt)!!.dateTime,
                    ApolloZonedDateTime.fromMuunZonedDateTime(swap.payedAt)?.dateTime,
                    swap.preimageInHex
            )

        }
    }
}
