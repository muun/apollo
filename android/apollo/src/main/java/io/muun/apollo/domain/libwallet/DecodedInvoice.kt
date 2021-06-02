package io.muun.apollo.domain.libwallet

import org.threeten.bp.ZonedDateTime

class DecodedInvoice(
    val original: String,
    val amountInSat: Long?,
    val description: String,
    val expirationTime: ZonedDateTime,
    val destinationPublicKey: String,
    val paymentHashHex: String
) {

    fun remainingMillis(): Long {
        val expirationTimeInMillis: Long = expirationTime.toEpochSecond() * 1000
        return expirationTimeInMillis - System.currentTimeMillis()
    }
}