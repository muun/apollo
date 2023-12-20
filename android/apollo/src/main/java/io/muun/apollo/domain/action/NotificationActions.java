package io.muun.apollo.domain.action;


import io.muun.apollo.data.external.AppStandbyBucketProvider;
import io.muun.apollo.data.net.HoustonClient;
import io.muun.apollo.data.preferences.NotificationRepository;
import io.muun.apollo.domain.NotificationProcessor;
import io.muun.apollo.domain.action.base.AsyncAction0;
import io.muun.apollo.domain.action.base.AsyncActionStore;
import io.muun.apollo.domain.errors.BugDetected;
import io.muun.apollo.domain.errors.notifications.NotificationProcessingError;
import io.muun.apollo.domain.model.NotificationReport;
import io.muun.common.api.beam.notification.NotificationJson;
import io.muun.common.api.messages.FulfillIncomingSwapMessage;

import android.os.Build;
import androidx.annotation.Nullable;
import rx.BackpressureOverflow;
import rx.Completable;
import rx.Observable;
import rx.Observable.Transformer;
import rx.Scheduler;
import rx.Single;
import rx.Subscription;
import rx.functions.Func1;
import rx.subjects.PublishSubject;
import timber.log.Timber;

import java.util.List;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import static io.muun.apollo.domain.action.NotificationActions_ExtensionsKt.asString;
import static io.muun.apollo.domain.action.NotificationActions_ExtensionsKt.mapIds;

@Singleton // important
public class NotificationActions implements NotificationPoller {

    // If too many NotificationReports accumulate, we'll save them into a buffer with the following
    // capacity. Dropping reports is not ideal, since Apollo will need to query Houston for missing
    // notifications, but it's not serious either. This can happen after regaining connectivity,
    // or after Muun services recover from a temporary failure.
    private static final int MAX_PENDING_REPORTS_BEFORE_DROP_OLDEST = 32;

    private final NotificationRepository notificationRepository;
    private final HoustonClient houstonClient;

    private final NotificationProcessor notificationProcessor;

    // NOTE: these 2 properties make this class stateful (and thus @Singleton). After moving the
    // subject to a centralized SubjectStore (could be refactored out of AsyncActionStore) this
    // action bag would be stateless.
    private final PublishSubject<NotificationReport> reportQueue;
    private Subscription reportQueueSub;
    private final Scheduler scheduler;

    public final AsyncAction0<Void> pullNotificationsAction;

    private final AppStandbyBucketProvider appStandbyBucketProvider;

    /**
     * Constructor.
     */
    @Inject
    public NotificationActions(NotificationRepository notificationRepository,
                               HoustonClient houstonClient,
                               AsyncActionStore asyncActionStore,
                               NotificationProcessor notificationProcessor,
                               AppStandbyBucketProvider appStandbyBucketProvider,
                               @Named("notificationScheduler") Scheduler notificationScheduler) {

        this.notificationRepository = notificationRepository;
        this.houstonClient = houstonClient;
        this.notificationProcessor = notificationProcessor;
        this.appStandbyBucketProvider = appStandbyBucketProvider;
        this.scheduler = notificationScheduler;
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
     * Pull the latest notifications from Houston.
     */
    public Observable<Void> pullNotifications() {

        return Observable.defer(() -> {

            final long lastProcessedId = notificationRepository.getLastProcessedId();
            Timber.i("[Notifications] Pulling... LastProcessedId: " + lastProcessedId);

            return houstonClient.fetchNotificationReportAfter(lastProcessedId)
                    .map(report -> {
                        onNotificationReport(report);
                        return null;
                    });
        });
    }

    /**
     * Process a notification report. Do not call directly.
     */
    private void processReport(final NotificationReport report) {

        NotificationReport reportToProcess = report;

        // If we have a gap between the report and what we last processed, ignore the report
        // and start processing from the start of the gap. We'll refetch a bit of data, but it
        // makes for simple code.
        if (reportToProcess.getPreviousId() > notificationRepository.getLastProcessedId()) {
            reportToProcess = fetchNotificationReport(notificationRepository.getLastProcessedId())
                    .toBlocking()
                    .value();
        }

        processNotificationList(reportToProcess.getPreview()).await();

        // We rely on the fact that we mark notifications that failed to process as processed
        // anyway. Without that the following loop might be infinite.
        while (notificationRepository.getLastProcessedId() < reportToProcess.getMaximumId()) {

            reportToProcess = fetchNotificationReport(notificationRepository.getLastProcessedId())
                    .toBlocking()
                    .value();

            processNotificationList(reportToProcess.getPreview()).await();

        }
    }

    /**
     * Process a list of notifications. Do not call directly.
     */
    private Completable processNotificationList(final List<NotificationJson> notifications) {

        return Completable.defer(() -> {
            final long lastIdBefore = notificationRepository.getLastProcessedId();

            Timber.i("[Notifications] Processing List: " + asString(mapIds(notifications)));

            return Observable.from(notifications)
                    .compose(forEach(this::processNotification))
                    .lastOrDefault(null)
                    .flatMap(ignored -> {
                        final long lastIdAfter = notificationRepository.getLastProcessedId();

                        if (lastIdAfter > lastIdBefore) {

                            return houstonClient.confirmNotificationsDeliveryUntil(
                                    lastIdAfter,
                                    Build.MODEL,
                                    String.valueOf(Build.VERSION.SDK_INT),
                                    appStandbyBucketProvider.current().toString()
                            );

                        } else {
                            return Observable.just(null);
                        }
                    })
                    .toCompletable()
                    ;
        });
    }

    /**
     * Process a single notification. Do not call directly.
     */
    private Completable processNotification(NotificationJson notification) {

        return Completable.defer(() -> {
            final String messageType = notification.messageType;
            final String bucket = appStandbyBucketProvider.current().toString();
            final Long id = notification.id;
            Timber.i("[Notifications] Processing (" + id + ") " + messageType + " - " + bucket);

            final long lastProcessedId = notificationRepository.getLastProcessedId();

            if (id <= lastProcessedId) {
                return Completable.complete(); // already processed!
            }

            if (notification.previousId != lastProcessedId) {
                throw NotificationProcessingError.fromMissingIds(notification, lastProcessedId);
            }

            final long processingFailures = notificationRepository.getProcessingFailures();
            return notificationProcessor.process(notification, processingFailures)
                    .onErrorComplete(cause -> {
                        notificationRepository.increaseProcessingFailures();

                        logBreadcrumb(id, messageType, processingFailures, cause);
                        Timber.e(NotificationProcessingError.fromCause(notification, cause));

                        if (processingFailures > 3) {
                            // Abort after 3 failed retries to avoid bricking clients
                            return true;
                        }

                        //noinspection RedundantIfStatement
                        if (messageType.equals(FulfillIncomingSwapMessage.SPEC.messageType)) {
                            // We don't allow skipping fulfills
                            return false;
                        }

                        return true; // skip notification, log the error
                    })
                    .doOnCompleted(() ->
                            notificationRepository.setLastProcessedId(id)
                    );
        });
    }

    private Single<NotificationReport> fetchNotificationReport(@Nullable Long afterId) {
        return houstonClient.fetchNotificationReportAfter(afterId).toSingle();
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
                        () -> Timber.e(
                                new BugDetected("NotificationReport queue too big: dropping oldest")
                        ),
                        BackpressureOverflow.ON_OVERFLOW_DROP_OLDEST
                )
                .observeOn(scheduler)
                .subscribe(report -> {
                    try {
                        processReport(report);
                    } catch (Exception e) {
                        Timber.e(e);
                    }
                });
    }

    /**
     * Transformer to run a task on each item of an Observable, logging errors if any.
     */
    private <T> Transformer<T, Void> forEach(Func1<T, Completable> createTask) {
        return (items) -> items
                .map(createTask)
                .onBackpressureBuffer(200)
                .concatMap(task -> task
                        .doOnError(Timber::e)
                        .toObservable()
                );
    }

    private void logBreadcrumb(Long id, String msgType, long failures, Throwable cause) {
        final String e = cause.getClass().getSimpleName() + ":" + cause.getLocalizedMessage();
        Timber.i(
                "[Notifications] Error: (" + id + ") " + msgType + " - " + failures + " - " + e
        );
    }
}
