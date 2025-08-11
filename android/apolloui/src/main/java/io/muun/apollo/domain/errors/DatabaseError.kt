package io.muun.apollo.domain.errors


class DatabaseError(message: String, cause: Throwable) : MuunError(message, cause)
