package io.muun.apollo.osmodules.background_actions

import androidx.work.ExistingWorkPolicy
import androidx.work.Operation
import androidx.work.WorkInfo
import androidx.work.WorkManager
import app_provided_data.AndroidBackgroundActionsBridge
import dagger.Lazy
import io.muun.apollo.data.os.OS
import io.muun.apollo.domain.model.background_actions.BackgroundActionsRunOrigin
import javax.inject.Inject
import javax.inject.Singleton

internal const val BACKGROUND_ACTIONS_WORK_NAME = "background_actions"

internal const val RUN_NOW_TAG = "background_actions:runNow"

private const val MAX_SCHEDULE_ATTEMPTS = 3

/**
 * Schedules Libwallet's background action wakes on WorkManager as a single unique work.
 * A run chains the next request behind itself, and nothing in the background cancels a running one.
 *
 * WorkManager is injected lazily because it needs to be initialized after the app is up.
 */
@Singleton
class BackgroundActionsScheduler @Inject constructor(
    private val lazyWorkManager: Lazy<WorkManager>,
) : AndroidBackgroundActionsBridge {

    private val workManager: WorkManager
        get() = lazyWorkManager.get()

    /**
     * Ensure there is a pending request scheduled to run in minDelayMillis.
     * A running request is never cancelled.
     * A retrying or pending foreground request is left alone.
     */
    @Synchronized
    override fun scheduleWake(minDelayMillis: Long) {
        repeat(MAX_SCHEDULE_ATTEMPTS) {
            val allRequests = getRequests()

            val pending = allRequests.pending()

            // If we have no pending request, we enqueue a new one.
            if (pending.isEmpty()) {
                enqueueBehindRunning(allRequests, minDelayMillis)
                return
            }

            // We update the delay of all pending background requests that haven't run yet.
            val success = pending
                .filter { RUN_NOW_TAG !in it.tags && it.runAttemptCount == 0 }
                .all { updateRequest(it, minDelayMillis) }

            if (success) {
                return
            }
        }

        throw IllegalStateException("Failed to schedule wake")
    }

    /**
     * Cancel the pending requests, leaving a running one alone.
     */
    @Synchronized
    override fun cancelWake() {
        val pending = getRequests().pending()

        for (request in pending) {
            workManager.cancelWorkById(request.id).get()
        }
    }

    /**
     * Replace whatever is pending with a request that runs now, unless a request is already
     * running.
     */
    @Synchronized
    fun runNow() {
        if (getRequests().any { it.state == WorkInfo.State.RUNNING }) {
            return
        }

        // Expedited jobs started on top never spend a session, even if the user leaves the app.
        val request = BackgroundActionsWorker.buildRequest(
            BackgroundActionsRunOrigin.RUN_NOW,
            tag = RUN_NOW_TAG,
            expedited = OS.supportsExpeditedJobs()
        )

        // REPLACE cancels any pending request. If it started since the check above, it is
        // cancelled mid-run, which is fine since this run does its work.
        workManager
            .enqueueUniqueWork(BACKGROUND_ACTIONS_WORK_NAME, ExistingWorkPolicy.REPLACE, request)
            .get()
    }

    private fun updateRequest(request: WorkInfo, minDelayMillis: Long): Boolean {
        // A blocked request counts its delay from when its parent finishes, an enqueued one from
        // when it was enqueued.
        val delayMillis = if (request.state == WorkInfo.State.ENQUEUED) {
            val enqueueTime = request.nextScheduleTimeMillis - request.initialDelayMillis
            now() - enqueueTime + minDelayMillis
        } else {
            minDelayMillis
        }

        val newRequest = BackgroundActionsWorker.buildRequest(
            BackgroundActionsRunOrigin.UPDATE_WAKE,
            delayMillis.coerceAtLeast(0),
            request.id
        )

        val result = workManager.updateWork(newRequest).get()

        return result != WorkManager.UpdateResult.NOT_APPLIED
    }

    private fun enqueueBehindRunning(requests: List<WorkInfo>, minDelayMillis: Long) {
        // With a cancelled or failed request under the name, APPEND_OR_REPLACE deletes every
        // request under it, the running one included.
        val shouldPrune = requests.any {
            it.state == WorkInfo.State.CANCELLED || it.state == WorkInfo.State.FAILED
        }
        if (shouldPrune) {
            workManager.pruneWork().get()
        }

        val request = BackgroundActionsWorker.buildRequest(
            BackgroundActionsRunOrigin.ENQUEUE_WAKE,
            minDelayMillis
        )

        workManager
            .enqueueUniqueWork(
                BACKGROUND_ACTIONS_WORK_NAME,
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                request
            )
            .get()
    }

    private fun List<WorkInfo>.pending(): List<WorkInfo> {
        return filter {
            it.state == WorkInfo.State.BLOCKED || it.state == WorkInfo.State.ENQUEUED
        }
    }

    private fun getRequests(): List<WorkInfo> {
        return workManager.getWorkInfosForUniqueWork(BACKGROUND_ACTIONS_WORK_NAME).get()
    }

    private fun now(): Long {
        return workManager.configuration.clock.currentTimeMillis()
    }

    private fun Operation.get() {
        result.get()
    }
}
