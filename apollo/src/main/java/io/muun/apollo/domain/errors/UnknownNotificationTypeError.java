package io.muun.apollo.domain.errors;


public class UnknownNotificationTypeError extends RuntimeException {

    public UnknownNotificationTypeError(String type) {
        super("Unknown notification type: " + type);
    }
}
