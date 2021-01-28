package io.muun.apollo.domain.errors

import com.google.firebase.messaging.RemoteMessage

class FcmMessageProcessingError(message: RemoteMessage, cause: Throwable): MuunError(cause) {

    init {
        metadata["from"] = message.from ?: "<unknown>"
        metadata["to"] = message.to ?: "<unknown>"
        metadata["messageId"] = message.messageId ?: "<unknown>"
        metadata["messageType"] = message.messageType ?: "<unknown>"
        metadata["message"] = message.data["message"] ?: "<empty>"
    }

}