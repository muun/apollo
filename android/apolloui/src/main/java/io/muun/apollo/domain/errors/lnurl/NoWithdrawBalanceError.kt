package io.muun.apollo.domain.errors.lnurl

import io.muun.apollo.domain.errors.ErrorClassification
import io.muun.apollo.domain.errors.MuunError

class NoWithdrawBalanceError(message: String, domain: String) : MuunError() {

    override val classification = ErrorClassification.EXPECTED

    init {
        metadata["service"] = domain
        metadata["message"] = message
    }
}
