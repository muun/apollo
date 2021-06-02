package io.muun.apollo.data.external;

import io.muun.apollo.domain.model.Contact;
import io.muun.apollo.domain.model.LnUrlWithdraw;
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
     * @param paymentHashHex the paymentHash (in hex) of invoice associated with the lnurl withdraw
     */
    void cancelLnUrlNotification(@NotNull String paymentHashHex);
}
