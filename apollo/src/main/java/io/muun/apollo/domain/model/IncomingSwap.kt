package io.muun.apollo.domain.model

import io.muun.apollo.domain.model.base.HoustonUuidModel

class IncomingSwap(
        id: Long?,
        houstonUuid: String,
        var paymentHash: ByteArray,
        var htlc: IncomingSwapHtlc,
        var sphinxPacket: ByteArray?
): HoustonUuidModel(id, houstonUuid)