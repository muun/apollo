package io.muun.apollo.domain.errors


import io.muun.common.exception.PotentialBug

class NullCurrencyBugError:
    MuunError("The primary currency preference was found to be null"), PotentialBug
