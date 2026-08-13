package io.muun.apollo.domain.libwallet.errors

import io.muun.apollo.domain.errors.ErrorClassification
import io.muun.apollo.domain.errors.MuunError

private var msg = "Libwallet failed to store fee bump functions"

class FeeBumpFunctionsStoreError(
    val functions: String, cause: Throwable
) : MuunError(msg, cause) {

    override val classification = ErrorClassification.UNEXPECTED

    init {
        metadata["functions"] = functions
    }
}