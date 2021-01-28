package io.muun.apollo.domain.errors


class UnknownNotificationTypeError(type: String):
    MuunError("Unknown notification type") {

    init {
        metadata["type"] = type
    }

}
