package io.muun.apollo.domain.action;


import io.muun.apollo.data.logging.Logger;
import io.muun.apollo.data.net.HoustonClient;
import io.muun.apollo.data.os.execution.ExecutionTransformerFactory;
import io.muun.apollo.data.preferences.NotificationRepository;
import io.muun.apollo.domain.NotificationProcessor;
import io.muun.apollo.domain.action.base.AsyncAction0;
import io.muun.apollo.domain.action.base.AsyncActionStore;
import io.muun.apollo.domain.errors.NotificationProcessingError;
import io.muun.apollo.domain.model.NotificationReport;
import io.muun.common.api.NotificationJson;

import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;

import rx.BackpressureOverflow;
import rx.Observable;
import rx.Subscription;
import rx.subjects.PublishSubject;

import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton // important
public class NotificationActions {

    // If too many NotificationReports accumulate, we'll save them into a buffer with the following
    // capacity. Dropping reports is not ideal, since Apollo will need to query Houston for missing
    // notifications, but it's not serious either. This can happen after regaining connectivity,
    // or after Muun services recover from a temporary failure.
    private static final int MAX_PENDING_REPORTS_BEFORE_DROP_OLDEST = 32;

    private final NotificationRepository notificationRepository;
    private final HoustonClient houstonClient;

    private final ExecutionTransformerFactory transformerFactory;

    private final NotificationProcessor notificationProcessor;

    // NOTE: these 2 properties make this class stateful (and thus @Singleton). After moving the
    // subject to a centralized SubjectStore (could be refactored out of AsyncActionStore) this
    // action bag would be stateless.
    private final PublishSubject<NotificationReport> reportQueue;
    private Subscription reportQueueSub;

    public final AsyncAction0<Void> pullNotificationsAction;

    /**
     * Constructor.
     */
    @Inject
    public NotificationActions(NotificationRepository notificationRepository,
                               HoustonClient houstonClient,
                               AsyncActionStore asyncActionStore,
                               ExecutionTransformerFactory transformerFactory,
                               NotificationProcessor notificationProcessor) {

        this.notificationRepository = notificationRepository;
        this.houstonClient = houstonClient;
        this.transformerFactory = transformerFactory;
        this.notificationProcessor = notificationProcessor;

        reportQueue = PublishSubject.create();

        pullNotificationsAction = asyncActionStore
                .get("notifications/pull", this::pullNotifications);
    }

    /**
     * Invoke when a new NotificationReportJson is received.
     */
    public synchronized void onNotificationReport(NotificationReport report) {
        if (reportQueueSub == null || reportQueueSub.isUnsubscribed()) {
            reportQueueSub = startProcessingQueue(reportQueue);
        }

        reportQueue.onNext(report);
    }

    /**
     * Pull the latest notifications from Hosuton.
     */
    public Observable<Void> pullNotifications() {
        Logger.debug("[Notifications] Pulling...");

        final long lastProccessedId = notificationRepository.getLastProcessedId();

        return fetchNotificationReport(lastProccessedId)
                .map(report -> {
                    onNotificationReport(report);
                    return null;
                });
    }

    /**
     * Synchronously process a notification report. Do not call directly.
     */
    @VisibleForTesting
    public void processReport(NotificationReport report) {

        final long lastProcessedId = notificationRepository.getLastProcessedId();

        final List<NotificationJson> notifications;

        if (report.isMissingNotifications(lastProcessedId)) {
            // This report starts later than expected (we missed past notifications), or ends
            // earlier (more notifications are available in Houston):
            notifications = houstonClient.fetchNotificationsAfter(lastProcessedId)
                    .toBlocking()
                    .first();
        } else {
            // The report contains everything we need. It may even contain some notifications we
            // already processed:
            notifications = report.getPreview();
        }

        // TODO: a relatively easy optimization here would be to use the report preview and
        // only fetch the missing notifications, instead of discarding those already obtained.
        // This could be done in parallel.
        processNotificationList(notifications);
    }

    /**
     * Synchronously process a list of notifications. Do not call directly.
     */
    @VisibleForTesting
    public void processNotificationList(List<NotificationJson> notifications) {
        final long lastProcessedIdBefore = notificationRepository.getLastProcessedId();

        for (NotificationJson notification: notifications) {
            try {
                processNotification(notification);
            } catch (RuntimeException error) {
                Logger.error(error);
                break;
            }
        }

        final long lastProcessedIdAfter = notificationRepository.getLastProcessedId();

        if (lastProcessedIdAfter > lastProcessedIdBefore) {
            houstonClient.confirmNotificationsDeliveryUntil(lastProcessedIdAfter)
                .toCompletable()
                .await();
        }
    }

    /**
     * Synchronously process a single notification. Do not call directly.
     */
    @VisibleForTesting
    public void processNotification(NotificationJson notification) {
        Logger.debug("[Notifications] Processing " + notification.messageType);

        final long lastProcessedId = notificationRepository.getLastProcessedId();

        if (notification.id <= lastProcessedId) {
            return; // already processed!
        }

        if (notification.previousId != lastProcessedId) {
            throw NotificationProcessingError.fromMissingIds(notification, lastProcessedId);
        }

        try {
            notificationProcessor.process(notification).await();
        } catch (Throwable error) {
            throw NotificationProcessingError.fromCause(notification, error);
        }

        notificationRepository.setLastProcessedId(notification.id);
    }

    private Observable<NotificationReport> fetchNotificationReport(@Nullable Long afterId) {
        // We'll create a fake NotificationReport from the notification list Houston gives us.
        // This is simply to put in the queue and process it using the same entry point as other
        // reports.
        return houstonClient.fetchNotificationsAfter(afterId).map(notifications ->
            new NotificationReport(
                    afterId,
                    notifications.isEmpty()
                            ? afterId
                            : notifications.get(notifications.size() - 1).id,
                    notifications
            )
        );
    }

    private Subscription startProcessingQueue(Observable<NotificationReport> queue) {
        // NOTE: using `observeOn` instead of `subscribeOn` here simplifies testing, by making
        // subscriptions happen synchronously and making the jump to another thread later.

        // The fact that this method is called lazily after construction is also a compromise to
        // allow testing. The way Mocks and Spies work, the `this` reference in the lambda below
        // needs to be captured *after* patching this object, or calls won't be registered.

        return queue
                .onBackpressureBuffer(
                        MAX_PENDING_REPORTS_BEFORE_DROP_OLDEST,
                        () -> Logger.error("NotificationReport queue overflow: dropping oldest"),
                        BackpressureOverflow.ON_OVERFLOW_DROP_OLDEST
                )
                .observeOn(transformerFactory.getBackgroundScheduler())
                .doOnNext(report -> {
                    try {
                        processReport(report);
                    } catch (Throwable error) {
                        Logger.error(error);
                    }
                })
                .subscribe();
    }
}
