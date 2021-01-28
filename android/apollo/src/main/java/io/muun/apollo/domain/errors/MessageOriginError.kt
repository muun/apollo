package io.muun.apollo.domain.errors

import io.muun.common.api.messages.MessageOrigin
import io.muun.common.api.messages.MessageSpec

class MessageOriginError(sessionId: String, msgId: Long, origin: MessageOrigin, spec: MessageSpec):
    MuunError("Received a message from an unexpected origin") {

    init {
        metadata["sessionUuid"] = sessionId
        metadata["messageId"] = msgId
        metadata["messageType"] = spec.messageType
        metadata["origin"] = origin.name
        metadata["allowedOrigin"] = spec.allowedOrigin.name
    }

}
