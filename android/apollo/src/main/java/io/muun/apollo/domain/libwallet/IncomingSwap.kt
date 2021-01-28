package io.muun.apollo.domain.libwallet

import io.muun.apollo.domain.libwallet.errors.UnfulfillableIncomingSwapError
import io.muun.apollo.domain.model.IncomingSwap
import io.muun.apollo.domain.model.IncomingSwapFulfillmentData
import io.muun.common.crypto.hd.PrivateKey
import io.muun.common.crypto.hd.PublicKey
import io.muun.common.utils.Encodings
import io.muun.common.utils.Preconditions
import libwallet.Libwallet
import org.bitcoinj.core.NetworkParameters
import javax.inject.Inject

open class IncomingSwap
@Inject constructor() {

    open fun signFulfillment(
            swap: io.muun.apollo.domain.model.IncomingSwap,
            data: IncomingSwapFulfillmentData,
            userKey: PrivateKey,
            muunKey: PublicKey,
            network: NetworkParameters
    ): ByteArray {

        val htlc = Preconditions.checkNotNull(swap.htlc)

        val incomingSwap = libwallet.IncomingSwap()
        incomingSwap.fulfillmentTx = data.fulfillmentTx
        incomingSwap.muunSignature = data.muunSignature
        incomingSwap.outputPath = data.outputPath
        incomingSwap.outputVersion = data.outputVersion.toLong()

        incomingSwap.htlcTx = htlc.htlcTx
        incomingSwap.paymentHash = swap.paymentHash
        incomingSwap.sphinx = swap.sphinxPacket
        incomingSwap.swapServerPublicKey = Encodings.bytesToHex(htlc.swapServerPublicKey)
        incomingSwap.htlcExpiration = htlc.expirationHeight
        incomingSwap.collectInSats = swap.collectInSats

        // unused for now but should eventually be provided by houston
        incomingSwap.htlcBlock = byteArrayOf()
        incomingSwap.confirmationTarget = 0
        incomingSwap.blockHeight = 0
        incomingSwap.merkleTree = byteArrayOf()

        try {
            return incomingSwap.verifyAndFulfill(
                LibwalletBridge.toLibwalletModel(userKey, network),
                LibwalletBridge.toLibwalletModel(muunKey, network),
                LibwalletBridge.toLibwalletModel(network)
            )
        } catch (e: Exception) {
            throw UnfulfillableIncomingSwapError(swap.houstonUuid, e)
        }
    }

    open fun verifyFulfillable(
        swap: IncomingSwap,
        userKey: PrivateKey,
        network: NetworkParameters
    ) {

        try {

            Libwallet.isInvoiceFulfillable(
                swap.paymentHash,
                swap.sphinxPacket,
                swap.paymentAmountInSats,
                LibwalletBridge.toLibwalletModel(userKey, network),
                LibwalletBridge.toLibwalletModel(network)
            )

        } catch (e: Exception) {
            throw UnfulfillableIncomingSwapError(swap.houstonUuid, e)
        }
    }

    open fun exposePreimage(swap: IncomingSwap): ByteArray {
        return Libwallet.exposePreimage(swap.paymentHash)
    }

}