package io.muun.apollo.domain.errors.passwd

import io.muun.apollo.domain.errors.MuunError

/**
 * We couldn't use the given password to decrypt the base private key, this may happen if the user
 * didn't have a password challenge (only affects users created before Feb 2018, and that didn't
 * have a successful login since), otherwise is pretty major error.
 */
class PasswordIntegrityError : MuunError("The password could not decrypt the base private key")
