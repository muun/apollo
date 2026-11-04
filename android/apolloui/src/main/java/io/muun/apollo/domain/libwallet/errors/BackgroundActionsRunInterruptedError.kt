package io.muun.apollo.domain.libwallet.errors

import io.muun.apollo.domain.errors.ErrorClassification
import io.muun.apollo.domain.errors.MuunError

/**
 * The background actions run was cancelled or ran out of time. Expected: the next run picks up
 * the work.
 */
class BackgroundActionsRunInterruptedError(
    val reason: Reason,
    cause: Throwable,
) : MuunError(cause) {

    enum class Reason { CANCELLED, TIMEOUT }

    override val classification = ErrorClassification.EXPECTED

    init {
        metadata["reason"] = reason.name
    }
}
