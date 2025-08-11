package io.muun.apollo.data.libwallet

data class UtxoScanUpdate(
    val scanComplete: Boolean,
    val scanStatus: String?,
    val address: String?,
    val amount: Long?,
)
