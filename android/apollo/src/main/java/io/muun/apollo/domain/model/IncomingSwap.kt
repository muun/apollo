package io.muun.apollo.domain.model

import io.muun.apollo.domain.libwallet.errors.UnfulfillableIncomingSwapError
import io.muun.apollo.domain.libwallet.toLibwallet
import io.muun.apollo.domain.model.base.HoustonUuidModel
import io.muun.common.crypto.hd.PrivateKey
import io.muun.common.crypto.hd.PublicKey
import io.muun.common.utils.Encodings
import org.bitcoinj.core.NetworkParameters
import timber.log.Timber

open class IncomingSwap(
    id: Long?,
    houstonUuid: String,
    private val paymentHash: ByteArray,
    val htlc: IncomingSwapHtlc?,
    private val sphinxPacket: ByteArray?,
    val collectInSats: Long,
    val paymentAmountInSats: Long,
    private var preimage: ByteArray?,
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
        val libwalletUserKey = userKey.toLibwallet(network)
        val libwalletNetwork = network.toLibwallet()

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
        network: NetworkParameters,
    ): FulfillmentResult {

        val libwalletUserKey = userKey.toLibwallet(network)
        val libwalletMuunKey = muunKey.toLibwallet(network)
        val libwalletNetwork = network.toLibwallet()
        val libwalletFullfillmentData = data.toLibwalletModel()

        debugLog(htlc!!, libwalletFullfillmentData, libwalletUserKey, libwalletMuunKey)

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

    /**
     * Debugging info for verifying fullfilmentTxs, htlcTxs, etc...
     * Note: if you're looking at this you'll probably be interested in some data that's only
     * available in libwallet. See: libwallet/incoming_swap.go.
     */
    private fun debugLog(
        htlc: IncomingSwapHtlc,
        fullfillmentData: libwallet.IncomingSwapFulfillmentData,
        userKey: libwallet.HDPrivateKey,
        muunKey: libwallet.HDPublicKey,
    ) {
        Timber.d("---IncomingSwap---")
        Timber.d("SphinxPacket: $sphinxPacketHex")
        Timber.d("PaymentHash: ${Encodings.bytesToHex(paymentHash)}")
        Timber.d("HtlcTx: ${Encodings.bytesToHex(htlc.htlcTx)}")
        Timber.d("HtlcExpirationHeight: ${htlc.expirationHeight}")
        Timber.d("HtlcSwapServerPublicKey: ${Encodings.bytesToHex(htlc.swapServerPublicKey)}")

        val merkleTree = fullfillmentData.merkleTree ?: byteArrayOf()
        val htlcBlock = fullfillmentData.htlcBlock ?: byteArrayOf()
        Timber.d("---IncomingSwapFulfillmentData---")
        Timber.d("FullfilmentTx: ${Encodings.bytesToHex(fullfillmentData.fulfillmentTx)}")
        Timber.d("MuunSignature: ${Encodings.bytesToHex(fullfillmentData.muunSignature)}")
        Timber.d("OuputVersion: ${fullfillmentData.outputVersion}")
        Timber.d("OuputPath: ${fullfillmentData.outputPath}")
        Timber.d("MerkleTree: ${Encodings.bytesToHex(merkleTree)}")
        Timber.d("HtlcBlock: ${Encodings.bytesToHex(htlcBlock)}")
        Timber.d("ConfTarget: ${fullfillmentData.confirmationTarget}")

        Timber.d("---UserKey---")
        Timber.d("Base58: ${userKey.string()}")
        Timber.d("Path: ${userKey.path}")
        Timber.d("Network: ${userKey.network.name()}")

        Timber.d("---muunKey---")
        Timber.d("Base58: ${muunKey.string()}")
        Timber.d("Path: ${muunKey.path}")
        Timber.d("Network: ${muunKey.network.name()}")
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