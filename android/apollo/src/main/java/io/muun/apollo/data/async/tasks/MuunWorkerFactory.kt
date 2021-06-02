package io.muun.apollo.data.async.tasks

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.Worker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import io.muun.apollo.data.external.DataComponentProvider
import io.muun.apollo.data.external.NotificationService
import io.muun.apollo.data.os.execution.ExecutionTransformerFactory
import io.muun.apollo.domain.action.UserActions
import io.muun.apollo.domain.errors.MuunError
import io.muun.common.utils.Preconditions
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
    lateinit var transformerFactory: ExecutionTransformerFactory

    @Inject
    lateinit var notificationService: NotificationService

    init {
        provider.dataComponent.inject(this)
    }

    override fun createWorker(appContext: Context,
                              workerClassName: String,
                              workerParameters: WorkerParameters): ListenableWorker {

        val workerClass = Class.forName(workerClassName)

        // Should be enforce by WorkManager API but still (why don't they use Class param?!)
        Preconditions.checkArgument(Worker::class.java.isAssignableFrom(workerClass))

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
                throw MuunError("Unknown worker class scheduled for work! $workerClassName")
            }
        }
    }
}