package io.muun.apollo.domain.model

class ForwardingPolicy(
        var identityKey: ByteArray,
        var feeBaseMsat: Long,
        var feeProportionalMillionths: Long,
        var cltvExpiryDelta: Long
)