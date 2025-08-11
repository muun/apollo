package io.muun.apollo.domain.errors.rc

import io.muun.apollo.domain.errors.MuunError

class FinishRecoveryCodeSetupError(cause: Throwable) : MuunError(cause)