package io.muun.apollo.domain.model

import io.muun.apollo.domain.libwallet.LibwalletBridge
import io.muun.apollo.domain.libwallet.errors.UnfulfillableIncomingSwapError
import io.muun.apollo.domain.model.base.HoustonUuidModel
import io.muun.common.crypto.hd.PrivateKey
import io.muun.common.crypto.hd.PublicKey
import io.muun.common.utils.Encodings
import io.muun.common.utils.Preconditions
import libwallet.HDPrivateKey
import libwallet.HDPublicKey
import org.bitcoinj.core.NetworkParameters

open class IncomingSwap(
        id: Long?,
        houstonUuid: String,
        val paymentHash: ByteArray,
        val htlc: IncomingSwapHtlc?,
        val sphinxPacket: ByteArray?,
        val collectInSats: Long,
        val paymentAmountInSats: Long,
        var preimage:  ByteArray?
): HoustonUuidModel(id, houstonUuid) {

    class FulfillmentResult(val fullfillmentTx: ByteArray?, val preimage: ByteArray)

    private fun toLibwalletModel(): libwallet.IncomingSwap {
        val swap = libwallet.IncomingSwap()

        swap.htlc = htlc?.toLibwalletModel()
        swap.paymentHash = paymentHash
        swap.sphinxPacket = sphinxPacket
        swap.collectSat = collectInSats
        swap.paymentAmountSat = paymentAmountInSats

        return swap
    }

    open fun verifyFulfillable(userKey: PrivateKey, network: NetworkParameters) {
        val userKey = LibwalletBridge.toLibwalletModel(userKey, network)
        val network = LibwalletBridge.toLibwalletModel(network)

        try {
            toLibwalletModel().verifyFulfillable(userKey, network)
        } catch (e: Exception) {
            throw UnfulfillableIncomingSwapError(houstonUuid, e)
        }
    }

    open fun fulfill(data: IncomingSwapFulfillmentData,
                userKey: PrivateKey, muunKey: PublicKey,
                network: NetworkParameters): FulfillmentResult {

        val userKey = LibwalletBridge.toLibwalletModel(userKey, network)
        val muunKey = LibwalletBridge.toLibwalletModel(muunKey, network)
        val network = LibwalletBridge.toLibwalletModel(network)

        try {
            val result = toLibwalletModel().fulfill(
                    data.toLibwalletModel(), userKey, muunKey, network)

            checkNotNull(result.fulfillmentTx)
            preimage = result.preimage

            return FulfillmentResult(result.fulfillmentTx, result.preimage)
        } catch (e: Exception) {
            throw UnfulfillableIncomingSwapError(houstonUuid, e)
        }
    }

    open fun fulfillFullDebt(): FulfillmentResult {
        val result = toLibwalletModel().fulfillFullDebt()

        check(result.fulfillmentTx == null)
        preimage = result.preimage

        return FulfillmentResult(result.fulfillmentTx, result.preimage)
    }

}