package io.muun.apollo.domain.errors


import io.muun.common.exception.PotentialBug

class NullExpectedDebtBugError : MuunError(
    "The expectedDebt, in NTS preference, was found to be null"
), PotentialBug
