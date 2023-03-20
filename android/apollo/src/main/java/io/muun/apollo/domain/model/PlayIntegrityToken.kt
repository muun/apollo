package io.muun.apollo.domain.model

data class PlayIntegrityToken(
    val token: String?,        // Null in case of error
    val error: String? = null, // Null in case of success
    val cause: String? = null, // NonNull for strange errors, when error code can't be parsed
)