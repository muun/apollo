package io.muun.apollo.domain.errors.notifications

import io.muun.apollo.domain.errors.MuunError
import io.muun.common.api.messages.MessageSpec
import io.muun.common.model.SessionStatus

class MessagePermissionsError(
    sessionUuid: String,
    messageId: Long,
    currentStatus: SessionStatus?,
    spec: MessageSpec,
) : MuunError("Received a message without the right permissions") {

    init {
        metadata["sessionUuid"] = sessionUuid
        metadata["sessionStatus"] = currentStatus?.name ?: "null"
        metadata["messageId"] = messageId
        metadata["messageType"] = spec.messageType
        metadata["allowedSessionStatus"] = spec.permission.name
    }
}