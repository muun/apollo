package io.muun.apollo.domain.model

import io.muun.apollo.domain.libwallet.errors.UnfulfillableIncomingSwapError
import io.muun.apollo.domain.libwallet.toLibwalletModel
import io.muun.apollo.domain.model.base.HoustonUuidModel
import io.muun.common.crypto.hd.PrivateKey
import io.muun.common.crypto.hd.PublicKey
import io.muun.common.utils.Encodings
import org.bitcoinj.core.NetworkParameters

open class IncomingSwap(
    id: Long?,
    houstonUuid: String,
    private val paymentHash: ByteArray,
    val htlc: IncomingSwapHtlc?,
    private val sphinxPacket: ByteArray?,
    val collectInSats: Long,
    val paymentAmountInSats: Long,
    private var preimage: ByteArray?
) : HoustonUuidModel(id, houstonUuid) {

    fun getPaymentHash() =
        Sha256Hash.fromBytes(paymentHash)

    fun getPreimage(): Preimage? =
        if (this.preimage != null) {
            Preimage.fromBytes(this.preimage!!)
        } else {
            null
        }

    val sphinxPacketHex: String?
        get() = if (this.sphinxPacket != null) {
            Encodings.bytesToHex(sphinxPacket)
        } else {
            null
        }

    open fun verifyFulfillable(userKey: PrivateKey, network: NetworkParameters) {
        val libwalletUserKey = userKey.toLibwalletModel(network)
        val libwalletNetwork = network.toLibwalletModel()

        try {
            toLibwalletModel().verifyFulfillable(libwalletUserKey, libwalletNetwork)
        } catch (e: Exception) {
            throw UnfulfillableIncomingSwapError(houstonUuid, e)
        }
    }

    open fun fulfill(
        data: IncomingSwapFulfillmentData,
        userKey: PrivateKey,
        muunKey: PublicKey,
        network: NetworkParameters
    ): FulfillmentResult {

        val libwalletUserKey = userKey.toLibwalletModel(network)
        val libwalletMuunKey = muunKey.toLibwalletModel(network)
        val libwalletNetwork = network.toLibwalletModel()
        val libwalletFullfillmentData = data.toLibwalletModel()

        try {
            val result = toLibwalletModel().fulfill(
                libwalletFullfillmentData,
                libwalletUserKey,
                libwalletMuunKey,
                libwalletNetwork
            )

            checkNotNull(result.fulfillmentTx)
            preimage = result.preimage

            return FulfillmentResult(result.fulfillmentTx, Preimage.fromBytes(result.preimage))
        } catch (e: Exception) {
            throw UnfulfillableIncomingSwapError(houstonUuid, e)
        }
    }

    open fun fulfillFullDebt(): FulfillmentResult {
        val result = toLibwalletModel().fulfillFullDebt()

        check(result.fulfillmentTx == null)
        preimage = result.preimage

        return FulfillmentResult(result.fulfillmentTx, Preimage.fromBytes(result.preimage))
    }

    private fun toLibwalletModel(): libwallet.IncomingSwap {
        val swap = libwallet.IncomingSwap()

        swap.htlc = htlc?.toLibwalletModel()
        swap.paymentHash = paymentHash
        swap.sphinxPacket = sphinxPacket
        swap.collectSat = collectInSats
        swap.paymentAmountSat = paymentAmountInSats

        return swap
    }

    class FulfillmentResult constructor(val fullfillmentTx: ByteArray?, val preimage: Preimage)

}