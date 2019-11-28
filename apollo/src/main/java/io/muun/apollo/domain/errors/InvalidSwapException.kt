package io.muun.apollo.domain.errors

import io.muun.common.exception.PotentialBug

class InvalidSwapException(swapUuid: String):
    MuunError("Validation failed for swap UUID $swapUuid"), PotentialBug
