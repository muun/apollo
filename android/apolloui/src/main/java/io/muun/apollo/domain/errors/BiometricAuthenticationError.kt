package io.muun.apollo.domain.errors

import io.muun.apollo.data.external.UserFacingErrorMessages
import io.muun.apollo.domain.model.BiometricAuthenticationErrorReason

class BiometricAuthenticationError(
    val reason: BiometricAuthenticationErrorReason,
) : UserFacingError(UserFacingErrorMessages.INSTANCE.biometricsAuthenticationError(reason)) {

    init {
        metadata["biometricAuthenticationErrorReason"] = reason
    }
}
