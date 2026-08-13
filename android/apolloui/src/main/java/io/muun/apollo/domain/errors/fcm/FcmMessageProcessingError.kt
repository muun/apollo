package io.muun.apollo.domain.errors.fcm

import com.google.firebase.messaging.RemoteMessage
import io.muun.apollo.domain.errors.ErrorClassification
import io.muun.apollo.domain.errors.MuunError

class FcmMessageProcessingError(message: RemoteMessage, cause: Throwable) : MuunError(cause) {

    override val classification = ErrorClassification.UNEXPECTED

    init {
        metadata["from"] = message.from ?: "<unknown>"
        metadata["to"] = message.to ?: "<unknown>"
        metadata["messageId"] = message.messageId ?: "<unknown>"
        metadata["messageType"] = message.messageType ?: "<unknown>"
        metadata["message"] = message.data["message"] ?: "<empty>"
    }

}