package io.muun.apollo.domain.errors

import io.muun.apollo.data.external.UserFacingErrorMessages
import io.muun.apollo.domain.model.BiometricAuthenticationErrorReason

class BiometricAuthenticationError(
    val reason: BiometricAuthenticationErrorReason,
) : UserFacingError(UserFacingErrorMessages.INSTANCE.biometricsAuthenticationError(reason)) {

    override val classification = ErrorClassification.EXPECTED

    init {
        metadata["biometricAuthenticationErrorReason"] = reason
    }
}
