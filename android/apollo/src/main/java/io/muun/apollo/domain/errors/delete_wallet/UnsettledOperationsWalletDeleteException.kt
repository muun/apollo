package io.muun.apollo.domain.errors.delete_wallet

import io.muun.apollo.domain.errors.MuunError

class UnsettledOperationsWalletDeleteException(cause: Throwable) : MuunError(cause)