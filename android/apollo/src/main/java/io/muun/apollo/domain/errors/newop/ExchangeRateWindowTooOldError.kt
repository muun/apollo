package io.muun.apollo.domain.errors.newop

import io.muun.apollo.domain.errors.MuunError
import io.muun.common.exception.PotentialBug

class ExchangeRateWindowTooOldError : MuunError(), PotentialBug
