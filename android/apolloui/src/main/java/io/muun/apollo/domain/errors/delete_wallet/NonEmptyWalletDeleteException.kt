package io.muun.apollo.domain.errors.delete_wallet

import io.muun.apollo.domain.errors.ErrorClassification
import io.muun.apollo.domain.errors.MuunError

class NonEmptyWalletDeleteException(cause: Throwable) : MuunError(cause) {
    override val classification = ErrorClassification.EXPECTED
}
