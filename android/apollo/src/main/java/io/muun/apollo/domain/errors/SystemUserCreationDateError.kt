package io.muun.apollo.domain.errors

import android.os.UserHandle

class SystemUserCreationDateError(
    userProfile: UserHandle,
    isSystemUser: Boolean,
    cause: Throwable,
) : HardwareCapabilityError("user creation date", cause) {

    init {
        metadata["userProfile"] = userProfile.toString()
        metadata["isSystemUser"] = isSystemUser
    }
}
