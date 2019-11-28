package io.muun.apollo.domain.errors

import io.muun.common.exception.PotentialBug

class NoPaymentRouteException(invoice: String, cause: Throwable):
    MuunError(invoice, cause), PotentialBug
