package io.muun.apollo.data.async.tasks

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import io.muun.apollo.data.external.DataComponentProvider
import io.muun.apollo.data.os.execution.ExecutionTransformerFactory
import io.muun.apollo.domain.action.UserActions
import javax.inject.Inject

/**
 * This is what WorkManager's API has to offer for dependency injection logic. Instantion of workers
 * happen on background and some of our injected dependencies (e.g ClipboardProvider) need to be
 * instantiated and injected on mainThread. For more info:
 * https://developer.android.com/topic/libraries/architecture/workmanager/advanced/custom-configuration
 */
class PeriodicTaskWorkerFactory(provider: DataComponentProvider) : WorkerFactory() {

    @Inject
    lateinit var taskDispatcher: TaskDispatcher

    @Inject
    lateinit var userActions: UserActions

    @Inject
    lateinit var transformerFactory: ExecutionTransformerFactory

    init {
        provider.dataComponent.inject(this)
    }

    override fun createWorker(appContext: Context,
                              workerClassName: String,
                              workerParameters: WorkerParameters): ListenableWorker? {
        return PeriodicTaskWorker(
            appContext,
            workerParameters,
            taskDispatcher,
            userActions,
            transformerFactory
        )
    }
}