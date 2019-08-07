package io.muun.apollo.domain.model

import io.muun.apollo.data.serialization.dates.ApolloZonedDateTime
import io.muun.apollo.domain.model.base.HoustonIdModel
import io.muun.common.api.HardwareWalletJson
import io.muun.common.crypto.hd.PublicKey
import io.muun.common.model.HardwareWalletBrand
import io.muun.common.utils.Preconditions
import org.threeten.bp.ZonedDateTime

data class HardwareWallet(override var id: Long?,
                          override val hid: Long = -1, // workaround TODO: caller should pass hid
                          val brand: HardwareWalletBrand,
                          val model: String,
                          val label: String,
                          val basePublicKey: PublicKey,
                          val createdAt: ZonedDateTime = ZonedDateTime.now(),
                          val lastPairedAt: ZonedDateTime = ZonedDateTime.now(),
                          val isPaired: Boolean) : HoustonIdModel(id, hid) {

    // This is a workaround for Java's inability to call constructor with optional params
    // TODO: kotlinize caller class and remove this
    constructor(brand: HardwareWalletBrand,
                model: String,
                label: String,
                basePublicKey: PublicKey,
                isPaired: Boolean) :
            this(null,
                    -1, // workaround TODO: caller should pass hid
                    brand,
                    model,
                    label,
                    basePublicKey,
                    ZonedDateTime.now(),
                    ZonedDateTime.now(),
                    isPaired)

    /**
     * Merge this HW with an updated copy, choosing whether to keep or replace each field.
     */
    fun mergeWithUpdate(other: HardwareWallet): HardwareWallet {
        Preconditions.checkArgument(basePublicKey == other.basePublicKey)

        return copy(
                hid = other.hid,
                brand = other.brand,
                model = other.model,
                label = other.label,
                createdAt = other.createdAt,
                lastPairedAt = other.lastPairedAt,
                isPaired = other.isPaired
        )
    }

    fun toJson() =
            HardwareWalletJson(
                    hid,
                    brand,
                    model,
                    label,
                    basePublicKey.toJson(),
                    ApolloZonedDateTime.of(createdAt),
                    ApolloZonedDateTime.of(lastPairedAt),
                    isPaired
            )

    companion object {

        fun fromJson(json: HardwareWalletJson?): HardwareWallet? {
            return if (json == null) {
                null

            } else return HardwareWallet(
                    null,
                    json.id!!,
                    json.brand,
                    json.model,
                    json.label,
                    PublicKey.fromJson(json.publicKey)!!,
                    ApolloZonedDateTime.fromMuunZonedDateTime(json.createdAt)!!.dateTime,
                    ApolloZonedDateTime.fromMuunZonedDateTime(json.lastPairedAt)!!.dateTime,
                    json.isPaired
            )

        }
    }
}
