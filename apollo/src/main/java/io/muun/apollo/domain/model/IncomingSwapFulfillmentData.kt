package io.muun.apollo.domain.model

class IncomingSwapFulfillmentData(
        val fulfillmentTx: ByteArray,
        val muunSignature:ByteArray,
        val outputPath: String,
        val outputVersion: Int
)