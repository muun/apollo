package io.muun.apollo.domain.model

import io.muun.apollo.domain.model.base.HoustonUuidModel

class IncomingSwap(
        id: Long?,
        houstonUuid: String,
        val paymentHash: ByteArray,
        val htlc: IncomingSwapHtlc?,
        val sphinxPacket: ByteArray?,
        val collectInSats: Long,
        val paymentAmountInSats: Long,
        var preimage:  ByteArray?
): HoustonUuidModel(id, houstonUuid)