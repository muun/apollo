package io.muun.apollo.domain.libwallet.errors

data class ErrorDetail(
    val type: ErrorDetailType,
    val code: Long,
    val message: String,
    val developerMessage: String,
)
