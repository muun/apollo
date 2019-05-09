package io.muun.apollo.external;

import io.muun.apollo.domain.model.Contact;
import io.muun.apollo.domain.model.Operation;

import javax.validation.constraints.NotNull;

public interface NotificationService {
    /**
     * Show a notification for a new incoming operation.
     */
    void showNewOperationNotification(@NotNull Operation operation);

    /**
     * Show a notification for a new contact.
     */
    void showNewContactNotification(@NotNull Contact contact);

    /**
     * Cancel all previously shown notifications.
     */
    void cancelAllNotifications();
}
