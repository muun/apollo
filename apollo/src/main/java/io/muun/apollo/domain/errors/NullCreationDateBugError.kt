package io.muun.apollo.domain.errors


import io.muun.common.exception.PotentialBug

class NullCreationDateBugError:
    MuunError("The User createdAt field was null"), PotentialBug
