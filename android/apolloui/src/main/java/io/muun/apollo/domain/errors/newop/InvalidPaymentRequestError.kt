package io.muun.apollo.domain.errors.newop


import io.muun.apollo.data.external.UserFacingErrorMessages
import io.muun.apollo.domain.errors.UserFacingError
import io.muun.common.exception.PotentialBug

class InvalidPaymentRequestError(innerMessage: String, cause: Throwable? = null) : UserFacingError(
    UserFacingErrorMessages.INSTANCE.invalidPaymentRequest(),
    RuntimeException(innerMessage, cause)
), PotentialBug
