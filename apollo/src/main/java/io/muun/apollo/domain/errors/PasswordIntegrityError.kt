package io.muun.apollo.domain.errors

/**
 * We couldn't use the given password to decrypt the root private key, this may happen if the user
 * didn't have a password challenge (only affects users created before Feb 2018, and that didn't
 * have a successful login since), otherwise is pretty major error.
 */
class PasswordIntegrityError:
    MuunError("The password could not decrypt the root private key")
