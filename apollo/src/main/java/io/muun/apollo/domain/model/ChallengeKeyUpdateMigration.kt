package io.muun.apollo.domain.model

data class ChallengeKeyUpdateMigration(
    val newPasswordKeySalt: ByteArray,
    val newRecoveryCodeKeySalt: ByteArray?,
    val newEncryptedMuunKey: String?
)