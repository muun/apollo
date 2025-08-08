package io.muun.apollo.domain.action

import android.os.Build
import io.muun.apollo.data.external.AppStandbyBucketProvider
import io.muun.apollo.data.net.HoustonClient
import io.muun.apollo.data.preferences.NotificationRepository
import io.muun.apollo.domain.NotificationProcessor
import io.muun.apollo.domain.action.base.AsyncAction0
import io.muun.apollo.domain.action.base.AsyncActionStore
import io.muun.apollo.domain.errors.BugDetected
import io.muun.apollo.domain.errors.notifications.NotificationProcessingError.Companion.fromCause
import io.muun.apollo.domain.errors.notifications.NotificationProcessingError.Companion.fromMissingIds
import io.muun.apollo.domain.model.NotificationReport
import io.muun.common.api.beam.notification.NotificationJson
import io.muun.common.api.messages.FulfillIncomingSwapMessage
import rx.BackpressureOverflow
import rx.Completable
import rx.Observable
import rx.Scheduler
import rx.Single
import rx.Subscription
import rx.functions.Func1
import rx.subjects.PublishSubject
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

enum class NotificationProcessingState {
    STARTED,
    COMPLETED
}

@Singleton // important
class NotificationActions @Inject constructor(
    private val notificationRepository: NotificationRepository,
    private val houstonClient: HoustonClient,
    asyncActionStore: AsyncActionStore,
    private val notificationProcessor: NotificationProcessor,
    private val appStandbyBucketProvider: AppStandbyBucketProvider,
    @param:Named("notificationScheduler") private val scheduler: Scheduler,
) : NotificationPoller {

    companion object {
        // If too many NotificationReports accumulate, we'll save them into a buffer with the following
        // capacity. Dropping reports is not ideal, since Apollo will need to query Houston for missing
        // notifications, but it's not serious either. This can happen after regaining connectivity,
        // or after Muun services recover from a temporary failure.
        private const val MAX_PENDING_REPORTS_BEFORE_DROP_OLDEST = 32
    }

    // NOTE: these 2 properties make this class stateful (and thus @Singleton). After moving the
    // subject to a centralized SubjectStore (could be refactored out of AsyncActionStore) this
    // action bag would be stateless.
    private val reportQueue: PublishSubject<NotificationReport> = PublishSubject.create()
    private var reportQueueSub: Subscription? = null

    // Notify about the notification processing state.
    private val processingSubject: PublishSubject<NotificationProcessingState> = PublishSubject.create()

    @JvmField
    val pullNotificationsAction: AsyncAction0<Void>

    init {
        pullNotificationsAction = asyncActionStore
            .get<Void>("notifications/pull") { pullNotifications() }
    }

    /**
     * Invoke when a new NotificationReportJson is received.
     */
    @Synchronized
    fun onNotificationReport(report: NotificationReport) {
        if (reportQueueSub == null || reportQueueSub!!.isUnsubscribed) {
            reportQueueSub = startProcessingQueue(reportQueue)
        }
        reportQueue.onNext(report)
    }

    /**
     * Stream to track the START and COMPLETION of processing a notification batch.
     */
    fun getNotificationProcessingState(): Observable<NotificationProcessingState> {
        return processingSubject
    }

    /**
     * Pull the latest notifications from Houston.
     */
    override fun pullNotifications(): Observable<Void> {
        return Observable.defer {
            val lastProcessedId = notificationRepository.lastProcessedId

            Timber.i("[Notifications] Pulling... LastProcessedId: $lastProcessedId")

            houstonClient.fetchNotificationReportAfter(lastProcessedId)
                .map { report: NotificationReport ->
                    onNotificationReport(report)
                    null
                }
        }
    }

    /**
     * Process a notification report. Do not call directly.
     */
    private fun processReport(report: NotificationReport) {
        var reportToProcess = report

        // If we have a gap between the report and what we last processed, ignore the report
        // and start processing from the start of the gap. We'll refetch a bit of data, but it
        // makes for simple code.
        if (reportToProcess.previousId > notificationRepository.lastProcessedId) {
            reportToProcess = fetchNotificationReport(notificationRepository.lastProcessedId)
                .toBlocking()
                .value()
        }
        processNotificationList(reportToProcess.preview).await()

        // We rely on the fact that we mark notifications that failed to process as processed
        // anyway. Without that the following loop might be infinite.
        while (notificationRepository.lastProcessedId < reportToProcess.maximumId) {
            reportToProcess = fetchNotificationReport(notificationRepository.lastProcessedId)
                .toBlocking()
                .value()
            processNotificationList(reportToProcess.preview).await()
        }
    }

    /**
     * Process a list of notifications. Do not call directly.
     */
    private fun processNotificationList(notifications: List<NotificationJson>): Completable {
        return Completable.defer {
            val lastIdBefore = notificationRepository.lastProcessedId

            Timber.i("[Notifications] Processing List: " + notifications.mapIds().asString())

            processingSubject.onNext(NotificationProcessingState.STARTED)

            Observable.from(notifications)
                .compose(forEach { notification: NotificationJson ->
                    processNotification(notification)
                })
                .lastOrDefault(null)
                .flatMap {
                    val lastIdAfter = notificationRepository.lastProcessedId
                    if (lastIdAfter > lastIdBefore) {
                        return@flatMap houstonClient.confirmNotificationsDeliveryUntil(
                            lastIdAfter,
                            Build.MODEL,
                            Build.VERSION.SDK_INT.toString(),
                            appStandbyBucketProvider.current().toString()
                        )
                    } else {
                        return@flatMap Observable.just(null)
                    }
                }
                .doOnCompleted {
                    processingSubject.onNext(NotificationProcessingState.COMPLETED)
                }
                .toCompletable()
        }
    }

    /**
     * Process a single notification. Do not call directly.
     */
    private fun processNotification(notification: NotificationJson): Completable {
        return Completable.defer {
            val messageType = notification.messageType
            val bucket = appStandbyBucketProvider.current().toString()
            val id = notification.id

            Timber.i("[Notifications] Processing ($id) $messageType - $bucket")

            val lastProcessedId = notificationRepository.lastProcessedId
            if (id <= lastProcessedId) {
                return@defer Completable.complete() // already processed!
            }

            if (notification.previousId != lastProcessedId) {
                throw fromMissingIds(notification, lastProcessedId)
            }

            val processingFailures = notificationRepository.processingFailures
            notificationProcessor.process(notification, processingFailures)
                .onErrorComplete { cause: Throwable ->
                    notificationRepository.increaseProcessingFailures()
                    logBreadcrumb(id, messageType, processingFailures, cause)
                    Timber.e(fromCause(notification, cause))
                    if (processingFailures > 3) {
                        // Abort after 3 failed retries to avoid bricking clients
                        return@onErrorComplete true
                    }
                    if (messageType == FulfillIncomingSwapMessage.SPEC.messageType) {
                        // We don't allow skipping fulfills
                        return@onErrorComplete false
                    }
                    true // skip notification, log the error
                }
                .doOnCompleted { notificationRepository.lastProcessedId = id }
        }
    }

    private fun fetchNotificationReport(afterId: Long): Single<NotificationReport> {
        return houstonClient.fetchNotificationReportAfter(afterId).toSingle()
    }

    private fun startProcessingQueue(queue: Observable<NotificationReport>): Subscription {
        // NOTE: using `observeOn` instead of `subscribeOn` here simplifies testing, by making
        // subscriptions happen synchronously and making the jump to another thread later.

        // The fact that this method is called lazily after construction is also a compromise to
        // allow testing. The way Mocks and Spies work, the `this` reference in the lambda below
        // needs to be captured *after* patching this object, or calls won't be registered.
        return queue
            .onBackpressureBuffer(
                MAX_PENDING_REPORTS_BEFORE_DROP_OLDEST.toLong(),
                {
                    Timber.e(BugDetected("NotificationReport queue too big: dropping oldest"))
                },
                BackpressureOverflow.ON_OVERFLOW_DROP_OLDEST
            )
            .observeOn(scheduler)
            .subscribe { report: NotificationReport ->
                try {
                    processReport(report)
                } catch (e: Exception) {
                    Timber.e(e)
                }
            }
    }

    /**
     * Transformer to run a task on each item of an Observable, logging errors if any.
     */
    private fun <T> forEach(createTask: Func1<T, Completable>): Observable.Transformer<T, Void> {
        return Observable.Transformer { items: Observable<T> ->
            items
                .map(createTask)
                .onBackpressureBuffer(200)
                .concatMap { task: Completable ->
                    task
                        .doOnError { t: Throwable -> Timber.e(t) }
                        .toObservable()
                }
        }
    }

    private fun logBreadcrumb(id: Long, msgType: String, failures: Long, cause: Throwable) {
        val e = cause.javaClass.simpleName + ":" + cause.localizedMessage
        Timber.i("[Notifications] Error: ($id) $msgType - $failures - $e")
    }
}