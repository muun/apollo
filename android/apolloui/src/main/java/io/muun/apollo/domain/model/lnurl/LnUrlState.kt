package io.muun.apollo.domain.model.lnurl

sealed class LnUrlState {
    data class Contacting(val domain: String) : LnUrlState()
    data class InvoiceCreated(val domain: String, val invoice: String) : LnUrlState()
    data class Receiving(val domain: String, val invoice: String) : LnUrlState()
    data class TakingTooLong(val domain: String) : LnUrlState()
    data class Failed(val error: LnUrlError) : LnUrlState()
    object Success : LnUrlState()
}