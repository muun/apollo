package io.muun.apollo.domain.model

import io.muun.common.api.KeySet

data class CreateSessionRcOk(
    val keySet: KeySet?,        // Null if hasEmailSetup = true
    @JvmField val hasEmailSetup: Boolean,
    val obfuscatedEmail: String?,    // Null if hasEmailSetup = false
    val playIntegrityNonce: String?,
)