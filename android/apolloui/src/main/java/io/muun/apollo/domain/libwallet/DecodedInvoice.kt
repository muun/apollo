package io.muun.apollo.domain.libwallet

import io.muun.apollo.domain.model.Sha256Hash
import org.bitcoinj.core.NetworkParameters
import org.threeten.bp.ZonedDateTime

class DecodedInvoice(
    val original: String,
    val amountInSat: Long?,
    val description: String,
    val expirationTime: ZonedDateTime,
    val paymentHash: Sha256Hash,
    val networkParameters: NetworkParameters,
) {

    fun remainingMillis(): Long {
        val expirationTimeInMillis: Long = expirationTime.toEpochSecond() * 1000
        return expirationTimeInMillis - System.currentTimeMillis()
    }
}