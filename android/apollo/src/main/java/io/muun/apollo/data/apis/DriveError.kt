package io.muun.apollo.data.apis

import com.google.android.gms.auth.UserRecoverableAuthException
import io.muun.common.utils.ExceptionUtils

class DriveError(cause: Throwable): RuntimeException(cause) {

    fun isMissingPermissions() =
        ExceptionUtils.getTypedCause(this, UserRecoverableAuthException::class.java).isPresent

}

