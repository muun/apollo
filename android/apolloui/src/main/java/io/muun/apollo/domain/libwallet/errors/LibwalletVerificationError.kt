package io.muun.apollo.domain.libwallet.errors

import io.muun.apollo.domain.errors.ErrorClassification
import io.muun.apollo.domain.errors.MuunError

private var msg = "Libwallet rejected the transaction during verification"

class LibwalletVerificationError(
    val failedChecks: String,
    val summary: String,
    val isAlternativeTx: Boolean,
) : MuunError("$msg: $failedChecks") {

    override val classification = ErrorClassification.UNEXPECTED

    init {
        metadata["failedChecks"] = failedChecks
        metadata["summary"] = summary
        metadata["isAlternativeTx"] = isAlternativeTx
    }
}
