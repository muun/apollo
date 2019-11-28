package io.muun.apollo.domain.errors

import io.muun.common.api.messages.MessageSpec

class MessageFromExpiredPairingError(sessionUuid: String, messageId: Long, spec: MessageSpec):
    MuunError("Received a message from an expired pairing") {

    init {
        metadata["sessionUuid"] = sessionUuid
        metadata["messageId"] = messageId
        metadata["spec"] = spec.messageType
    }
}