package io.muun.apollo.domain.libwallet.errors

import io.muun.apollo.domain.errors.ErrorClassification
import io.muun.apollo.domain.errors.MuunError

private var msg = "Libwallet reported checks it doesn't enforce yet"

class LibwalletIncubatingCheckError(
    val failedChecks: String,
    val summary: String,
    val isAlternativeTx: Boolean,
    val expectedDebtInSat: Long,
) : MuunError("$msg: $failedChecks") {

    override val classification = ErrorClassification.UNEXPECTED

    init {
        metadata["failedChecks"] = failedChecks
        metadata["summary"] = summary
        metadata["isAlternativeTx"] = isAlternativeTx
        metadata["expectedDebtInSat"] = expectedDebtInSat
    }
}
