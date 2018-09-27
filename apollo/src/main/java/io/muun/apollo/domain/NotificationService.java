package io.muun.apollo.domain;

import io.muun.apollo.domain.model.Contact;
import io.muun.apollo.domain.model.Operation;

public interface NotificationService {

    void cancelAllNotifications();

    void showNewContactNotification(Contact contact);

    void showNewOperationNotification(Operation operation);
}
