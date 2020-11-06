package io.muun.apollo.domain.errors

class CyclicalSwapError(invoice: String, cause: Throwable): MuunError(invoice, cause)