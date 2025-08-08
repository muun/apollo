package io.muun.apollo.data.async.tasks

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.Worker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import io.muun.apollo.data.external.DataComponentProvider
import io.muun.apollo.data.external.NotificationService
import io.muun.apollo.data.os.execution.ExecutionTransformerFactory
import io.muun.apollo.domain.LoggingContextManager
import io.muun.apollo.domain.action.UserActions
import io.muun.common.utils.Preconditions
import timber.log.Timber
import javax.inject.Inject

/**
 * This is what WorkManager's API has to offer for dependency injection logic. Instantion of workers
 * happen on background and some of our injected dependencies (e.g ClipboardProvider) need to be
 * instantiated and injected on mainThread. For more info:
 * https://developer.android.com/topic/libraries/architecture/workmanager/advanced/custom-configuration
 */
class MuunWorkerFactory(provider: DataComponentProvider) : WorkerFactory() {

    @Inject
    lateinit var taskDispatcher: TaskDispatcher

    @Inject
    lateinit var userActions: UserActions

    @Inject
    lateinit var loggingContextManager: LoggingContextManager

    @Inject
    lateinit var transformerFactory: ExecutionTransformerFactory

    @Inject
    lateinit var notificationService: NotificationService

    init {
        Timber.d("[MuunWorkerFactory] Execute Dependency Injection")
        provider.dataComponent.inject(this)
    }

    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters,
    ): ListenableWorker? {

        Timber.d("[MuunWorkerFactory] Create worker for $workerClassName")
        val workerClass = Class.forName(workerClassName)

        // Should be enforce by WorkManager API but still (why don't they use Class param?!)
        Preconditions.checkArgument(Worker::class.java.isAssignableFrom(workerClass))

        loggingContextManager.setupCrashlytics()

        when (workerClass) {
            PeriodicTaskWorker::class.java -> {

                return PeriodicTaskWorker(
                    appContext,
                    workerParameters,
                    taskDispatcher,
                    userActions,
                    transformerFactory
                )

            }

            LnPaymentFailedNotificationWorker::class.java -> {
                return LnPaymentFailedNotificationWorker(
                    appContext,
                    workerParameters,
                    notificationService
                )
            }

            else -> {
                // This tells WorkManager that your factory can't create this Worker, and it will
                // fall back to its default method of Worker creation.
                // See: https://github.com/square/leakcanary/issues/2283
                return null
            }
        }
    }
}