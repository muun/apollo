package io.muun.apollo.domain.model

import io.muun.apollo.domain.model.base.HoustonUuidModel

class IncomingSwapHtlc(
        id: Long?,
        houstonUuid: String,
        val expirationHeight: Long,
        val paymentAmountInSats: Long,
        val fulfillmentFeeSubsidyInSats: Long,
        val lentInSats: Long,
        val swapServerPublicKey: ByteArray,
        val fulfillmentTx: ByteArray?, // Present only if the swap is fulfilled
        val address: String,
        val outputAmountInSatoshis: Long,
        val htlcTx: ByteArray
): HoustonUuidModel(id, houstonUuid)
