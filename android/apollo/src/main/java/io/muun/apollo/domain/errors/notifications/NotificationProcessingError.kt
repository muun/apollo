package io.muun.apollo.domain.errors.notifications


import io.muun.apollo.domain.errors.MuunError
import io.muun.common.api.beam.notification.NotificationJson
import io.muun.common.exception.PotentialBug

class NotificationProcessingError : MuunError, PotentialBug {

    companion object {

        /** Create an Error caused by a gap in the notification processing ID sequence. */
        @JvmStatic
        fun fromMissingIds(notification: NotificationJson, lastProcessedId: Long) =
            NotificationProcessingError()
                .apply {
                    addNotificationMetadata(notification)
                    metadata["lastProcessedId"] = lastProcessedId
                }

        /** Create an Error caused by a lower-level problem during notification processing. */
        @JvmStatic
        fun fromCause(notification: NotificationJson, cause: Throwable) =
            NotificationProcessingError(cause)
                .apply {
                    addNotificationMetadata(notification)
                }
    }

    private fun addNotificationMetadata(notification: NotificationJson) {
        metadata["notificationId"] = notification.id
        metadata["notificationPreviousId"] = notification.previousId
        metadata["messageType"] = notification.messageType
        metadata["senderSessionUuid"] = notification.senderSessionUuid
    }

    private constructor() :
        super("DANGER! ERROR while processing notification")

    private constructor(cause: Throwable) :
        super("DANGER! ERROR while processing notification", cause)
}
