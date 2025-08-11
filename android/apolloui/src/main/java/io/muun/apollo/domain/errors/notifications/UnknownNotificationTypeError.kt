package io.muun.apollo.domain.errors.notifications

import io.muun.apollo.domain.errors.MuunError


class UnknownNotificationTypeError(type: String) :
    MuunError("Unknown notification type") {

    init {
        metadata["type"] = type
    }

}
