package io.muun.apollo.data.external;

import io.muun.apollo.domain.model.Contact;
import io.muun.apollo.domain.model.LnUrlWithdraw;
import io.muun.apollo.domain.model.Operation;
import io.muun.apollo.domain.model.Sha256Hash;
import io.muun.common.api.messages.EventCommunicationMessage;

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
     * Show a notification for a failed operation.
     */
    void showOperationFailedNotification(long opId);

    /**
     * Show an ONGOING notification to notify user of an incomplete incoming ln payment.
     */
    void showWaitingForLnPaymentNotification(@NotNull LnUrlWithdraw lnUrlWithdraw);

    /**
     * Schedule a notification for ln payment expiration and also stop waiting for it and cancel any
     * ONGOING notification.
     */
    void scheduleLnPaymentExpirationNotification(@NotNull LnUrlWithdraw lnUrlWithdraw);

    /**
     * Show a notification for ln payment expiration and also stop waiting for it and cancel any
     * ONGOING notification.
     */
    void showLnPaymentExpiredNotification(@NotNull LnUrlWithdraw lnUrlWithdraw);

    /**
     * Show a notification for an incoming lightning payment that is pending.
     */
    void showIncomingLightningPaymentPending();

    /**
     * Show a notification when Muun sends information about important events.
     */
    void showEventCommunication(@NotNull EventCommunicationMessage.Event event);

    /**
     * Cancel all previously shown notifications.
     */
    void cancelAllNotifications();

    /**
     * Cancel a previously shown notification.
     * @param id the ID of the notification
     */
    void cancelNotification(int id);

    /**
     * Cancel a previously shown LNURL withdraw notification.
     * @param paymentHash the paymentHash of invoice associated with the lnurl withdraw
     */
    void cancelLnUrlNotification(@NotNull Sha256Hash paymentHash);
}
