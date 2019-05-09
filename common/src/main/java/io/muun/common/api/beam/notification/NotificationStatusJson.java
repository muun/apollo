package io.muun.common.api.beam.notification;

public enum NotificationStatusJson {

    // Satellite created a message to be sent to apollo
    CREATED,
    // Message is in the process of being delivered to apollo
    SENT,
    // Message was successfully delivered to apollo
    DELIVERED,
    // Message failed to be delivered
    FAILED
}
