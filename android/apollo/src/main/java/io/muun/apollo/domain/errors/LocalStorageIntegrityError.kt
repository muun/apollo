package io.muun.apollo.domain.errors

/**
 * AKA our canary for when things go wrong with a logout (our logout logic has become a bit
 * cumbersome) or a local storage wipe.
 */
class LocalStorageIntegrityError(validSessionButNoJwt: Boolean, jwtButInvalidSession: Boolean):
    MuunError("Integrity error! Probably something went wrong with a logout") {

    init {
        metadata["hasValidSessionButNoJwt"] = validSessionButNoJwt
        metadata["hasJwtButInvalidSession"] = jwtButInvalidSession
    }
}
