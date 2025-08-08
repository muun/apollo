package io.muun.apollo.data.nfc

@Suppress("ArrayInDataClass")
data class CardResponse(
    val response: ByteArray,
    val statusCode: Int,
)