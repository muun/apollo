package io.muun.apollo.domain.errors


import io.muun.apollo.data.external.UserFacingErrorMessages
import io.muun.common.exception.PotentialBug

class InvalidPaymentRequestError
    @JvmOverloads constructor(innerMessage: String, cause: Throwable? = null): UserFacingError(
        UserFacingErrorMessages.INSTANCE.invalidPaymentRequest(),
        RuntimeException(innerMessage, cause)
    ), PotentialBug
