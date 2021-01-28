package io.muun.apollo.domain.errors

import io.muun.common.exception.PotentialBug

class UnreachableNodeException(invoice: String, cause: Throwable):
    MuunError(invoice, cause), PotentialBug
