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
                    val willPreOpenChannel: Boolean,
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
                    willPreOpenChannel,
                    ApolloZonedDateTime.fromNullable(payedAt),
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
                    SubmarineSwapFees.fromJson(swap.fees),
                    ApolloZonedDateTime.fromMuunZonedDateTime(swap.expiresAt)!!.dateTime,
                    swap.willPreOpenChannel,
                    ApolloZonedDateTime.fromMuunZonedDateTime(swap.payedAt)?.dateTime,
                    swap.preimageInHex
            )

        }
    }
}
