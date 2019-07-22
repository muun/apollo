package io.muun.apollo.domain.model

import io.muun.apollo.data.serialization.dates.ApolloZonedDateTime
import io.muun.apollo.domain.model.base.HoustonUuidModel
import io.muun.common.api.SubmarineSwapFundingOutputJson
import io.muun.common.api.SubmarineSwapJson
import io.muun.common.api.SubmarineSwapReceiverJson
import io.muun.common.utils.Encodings
import org.spongycastle.asn1.x500.style.RFC4519Style.description

import org.threeten.bp.ZonedDateTime

class SubmarineSwap(id: Long?,
                    houstonUuid: String,
                    val invoice: String,
                    val receiver: SubmarineSwapReceiver,
                    val fundingOutput: SubmarineSwapFundingOutput,
                    val sweepFeeInSatoshis: Long,
                    val lightningFeeInSatoshis: Long,
                    val expiresAt: ZonedDateTime,
                    var payedAt: ZonedDateTime?,         // may not be payed yet
                    var preimageInHex: String?) : HoustonUuidModel(id, houstonUuid) {

    val outputAmountInSatoshis: Long
        get() = fundingOutput.outputAmountInSatoshis

    fun toJson() =
            SubmarineSwapJson(
                    houstonUuid,
                    invoice,
                    receiver.toJson(),
                    fundingOutput.toJson(),
                    sweepFeeInSatoshis,
                    lightningFeeInSatoshis,
                    ApolloZonedDateTime.of(expiresAt),
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
                    swap.sweepFeeInSatoshis,
                    swap.lightningFeeInSatoshis,
                    ApolloZonedDateTime.fromMuunZonedDateTime(swap.expiresAt)!!.dateTime,
                    ApolloZonedDateTime.fromMuunZonedDateTime(swap.payedAt)?.dateTime,
                    swap.preimageInHex
            )

        }
    }
}
