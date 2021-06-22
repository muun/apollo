package io.muun.apollo.domain.libwallet

import io.muun.apollo.domain.model.Sha256Hash
import org.threeten.bp.ZonedDateTime

class DecodedInvoice constructor(
    val original: String,
    val amountInSat: Long?,
    val description: String,
    val expirationTime: ZonedDateTime,
    val destinationPublicKey: String,
    val paymentHash: Sha256Hash
) {

    fun remainingMillis(): Long {
        val expirationTimeInMillis: Long = expirationTime.toEpochSecond() * 1000
        return expirationTimeInMillis - System.currentTimeMillis()
    }
}