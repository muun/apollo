package io.muun.apollo.domain.model

import io.muun.apollo.domain.model.base.HoustonUuidModel

class IncomingSwapHtlc(
        id: Long?,
        houstonUuid: String,
        val expirationHeight: Long,
        val fulfillmentFeeSubsidyInSats: Long,
        val lentInSats: Long,
        val swapServerPublicKey: ByteArray,
        val fulfillmentTx: ByteArray?, // Present only if the swap is fulfilled
        val address: String,
        val outputAmountInSatoshis: Long,
        val htlcTx: ByteArray,
): HoustonUuidModel(id, houstonUuid) {

    fun toLibwalletModel(): libwallet.IncomingSwapHtlc {
        val htlc = libwallet.IncomingSwapHtlc()
        htlc.htlcTx = htlcTx
        htlc.expirationHeight = expirationHeight
        htlc.swapServerPublicKey = swapServerPublicKey

        return htlc;
    }
}
