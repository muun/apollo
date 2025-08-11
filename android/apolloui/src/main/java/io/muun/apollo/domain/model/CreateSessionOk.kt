package io.muun.apollo.domain.model

data class CreateSessionOk(
    val isExistingUser: Boolean,
    @JvmField val canUseRecoveryCode: Boolean,
    val playIntegrityNonce: String?,
)