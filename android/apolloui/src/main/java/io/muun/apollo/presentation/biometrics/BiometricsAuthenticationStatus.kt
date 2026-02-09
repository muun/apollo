package io.muun.apollo.presentation.biometrics

data class BiometricsAuthenticationStatus private constructor(
    val canAuthenticate: Boolean,
    val cannotAuthenticateReason: String? = null
) {

    companion object {

        fun enabled() = BiometricsAuthenticationStatus(
            canAuthenticate = true
        )

        fun disabled(reason: String) = BiometricsAuthenticationStatus(
            canAuthenticate = false,
            cannotAuthenticateReason = reason
        )
    }
}
