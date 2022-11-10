package io.muun.apollo.domain.errors.newop

import io.muun.apollo.domain.errors.MuunError
import io.muun.common.exception.PotentialBug

class InvalidSwapException(swapUuid: String) : MuunError(
    "Validation failed for swap UUID $swapUuid"
), PotentialBug
