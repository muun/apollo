package io.muun.apollo.domain.errors

open class SecureStorageError: MuunError {

    constructor()

    constructor(throwable: Throwable):
        super(throwable)

}
