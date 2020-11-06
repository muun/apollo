package io.muun.common.api.beam.notification;

public enum NotificationPriorityJson {

    // Deliver the notification whenever possible (ie. background stuff)
    LOW,
    // Deliver the notification as fast as possible (ie. critical information)
    HIGH,
    // Time sensitive notification that requires waking the app up.
    REAL_TIME
}
