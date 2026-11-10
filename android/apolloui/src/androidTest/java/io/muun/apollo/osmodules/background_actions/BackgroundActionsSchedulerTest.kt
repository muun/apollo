package io.muun.apollo.osmodules.background_actions

import android.content.Context
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.Clock
import androidx.work.Configuration
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.ListenableWorker.Result
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.impl.WorkManagerImpl
import androidx.work.testing.TestDriver
import androidx.work.testing.WorkManagerTestInitHelper
import androidx.work.testing.WorkManagerTestInitHelper.ExecutorsMode
import dagger.Lazy
import io.muun.apollo.data.os.OS
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Collections
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

private const val ONE_MINUTE_MILLIS = 60_000L
private const val HALF_MINUTE_MILLIS = 30_000L
private const val RUN_TIMEOUT_MILLIS = 10_000L

@RunWith(AndroidJUnit4::class)
class BackgroundActionsSchedulerTest {

    private class FakeClock(var now: Long) : Clock {
        override fun currentTimeMillis(): Long {
            return now
        }
    }

    private inner class StubWorker(
        context: Context,
        params: WorkerParameters,
    ) : Worker(context, params) {

        override fun doWork(): Result {
            runInputs[id] = inputData
            try {
                return onRun()
            } finally {
                finishedRuns.add(id)
            }
        }
    }

    private lateinit var context: Context
    private lateinit var driver: TestDriver
    private lateinit var workManager: WorkManager
    private lateinit var scheduler: BackgroundActionsScheduler

    // The test scheduler compares backoff against the real time, so the clock starts there.
    private val clock = FakeClock(System.currentTimeMillis())

    private val finishedRuns: MutableSet<UUID> = Collections.synchronizedSet(mutableSetOf())

    private val runInputs: MutableMap<UUID, Data> = Collections.synchronizedMap(mutableMapOf())

    private var onRun: () -> Result = { Result.success() }

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext

        val factory = object : WorkerFactory() {
            override fun createWorker(
                appContext: Context,
                workerClassName: String,
                workerParameters: WorkerParameters,
            ): ListenableWorker {
                return StubWorker(appContext, workerParameters)
            }
        }

        // Workers run on their own executor: a worker blocking on WorkManager APIs would
        // deadlock with synchronous executors.
        val configuration = Configuration.Builder()
            .setClock(clock)
            .setWorkerFactory(factory)
            .build()

        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            configuration,
            ExecutorsMode.PRESERVE_EXECUTORS
        )
        driver = requireNotNull(WorkManagerTestInitHelper.getTestDriver(context))
        workManager = WorkManager.getInstance(context)

        scheduler = BackgroundActionsScheduler(Lazy { workManager })
    }

    @After
    fun tearDown() {
        WorkManagerTestInitHelper.closeWorkDatabase()
        // The test WorkManager replaces the app's for the whole process, and the testing library
        // has no way to remove it, so later tests would get a closed one.
        WorkManagerImpl.setDelegate(null)
    }

    @Test
    fun scheduleWakeEnqueuesABackgroundRequest() {
        scheduler.scheduleWake(ONE_MINUTE_MILLIS)

        val request = requests().single()
        assertThat(request.state).isEqualTo(WorkInfo.State.ENQUEUED)
        assertThat(request.nextScheduleTimeMillis).isEqualTo(clock.now + ONE_MINUTE_MILLIS)
        assertThat(request.tags).doesNotContain(RUN_NOW_TAG)

        runWake(request.id) { Result.success() }
        assertThat(ranExpedited(request.id)).isFalse
    }

    @Test
    fun scheduleWakeMovesAnEnqueuedRequest() {
        scheduler.scheduleWake(ONE_MINUTE_MILLIS)
        val enqueued = requests().single()

        clock.now += HALF_MINUTE_MILLIS
        scheduler.scheduleWake(ONE_MINUTE_MILLIS)

        val moved = requests().single()
        assertThat(moved.id).isEqualTo(enqueued.id)
        assertThat(moved.state).isEqualTo(WorkInfo.State.ENQUEUED)
        assertThat(moved.nextScheduleTimeMillis).isEqualTo(clock.now + ONE_MINUTE_MILLIS)
    }

    @Test
    fun scheduleWakeMovesABlockedRequest() {
        val (before, after) = duringRun {
            scheduler.scheduleWake(ONE_MINUTE_MILLIS)
            val before = requestIn(WorkInfo.State.BLOCKED)
            scheduler.scheduleWake(HALF_MINUTE_MILLIS)
            before to requestIn(WorkInfo.State.BLOCKED)
        }

        assertThat(after.id).isEqualTo(before.id)
        assertThat(after.initialDelayMillis).isEqualTo(HALF_MINUTE_MILLIS)
    }

    @Test
    fun scheduleWakeLeavesARetryingRequest() {
        val id = scheduleRetryingWake()

        scheduler.scheduleWake(10)

        val retrying = requests().single()
        assertThat(retrying.id).isEqualTo(id)
        assertThat(retrying.state).isEqualTo(WorkInfo.State.ENQUEUED)
        assertThat(retrying.runAttemptCount).isEqualTo(1)
        assertThat(retrying.initialDelayMillis).isEqualTo(0)
    }

    @Test
    fun scheduleWakeLeavesAPendingForegroundRequest() {
        scheduler.runNow()
        val foregroundRequest = requests().single()

        scheduler.scheduleWake(ONE_MINUTE_MILLIS)

        val pending = requests().single()
        assertThat(pending.id).isEqualTo(foregroundRequest.id)
        assertThat(pending.initialDelayMillis).isEqualTo(0)
        assertThat(pending.tags).contains(RUN_NOW_TAG)
    }

    @Test
    fun scheduleWakeFromARunChainsTheNextWake() {
        val blocked = duringRun {
            scheduler.scheduleWake(ONE_MINUTE_MILLIS)
            requestIn(WorkInfo.State.BLOCKED)
        }

        val child = requestIn(WorkInfo.State.ENQUEUED)
        assertThat(child.id).isEqualTo(blocked.id)
        assertThat(child.nextScheduleTimeMillis).isEqualTo(clock.now + ONE_MINUTE_MILLIS)
    }

    @Test
    fun scheduleWakeAfterAFinishedRunEnqueuesANewRequest() {
        scheduler.scheduleWake(0)
        val finished = requests().single()
        runWake(finished.id) { Result.success() }

        scheduler.scheduleWake(ONE_MINUTE_MILLIS)

        val request = requestIn(WorkInfo.State.ENQUEUED)
        assertThat(request.id).isNotEqualTo(finished.id)
        assertThat(request.nextScheduleTimeMillis).isEqualTo(clock.now + ONE_MINUTE_MILLIS)
    }

    @Test
    fun scheduleWakeKeepsTheRunningRequestDespiteCancelledOnes() {
        val statesDuringRun = duringRun {
            scheduler.scheduleWake(ONE_MINUTE_MILLIS)
            scheduler.cancelWake()
            scheduler.scheduleWake(ONE_MINUTE_MILLIS)
            states()
        }

        assertThat(statesDuringRun)
            .containsExactlyInAnyOrder(WorkInfo.State.RUNNING, WorkInfo.State.BLOCKED)
        assertThat(states())
            .containsExactlyInAnyOrder(WorkInfo.State.SUCCEEDED, WorkInfo.State.ENQUEUED)
        assertThat(requestIn(WorkInfo.State.ENQUEUED).nextScheduleTimeMillis)
            .isEqualTo(clock.now + ONE_MINUTE_MILLIS)
    }

    @Test
    fun cancelWakeCancelsAnEnqueuedRequest() {
        scheduler.scheduleWake(ONE_MINUTE_MILLIS)

        scheduler.cancelWake()

        assertThat(requests().single().state).isEqualTo(WorkInfo.State.CANCELLED)
    }

    @Test
    fun cancelWakeCancelsARetryingRequest() {
        val id = scheduleRetryingWake()

        scheduler.cancelWake()

        assertThat(workInfo(id).state).isEqualTo(WorkInfo.State.CANCELLED)
    }

    @Test
    fun cancelWakeCancelsTheBlockedRequestButNotTheRunningOne() {
        val statesDuringRun = duringRun {
            scheduler.scheduleWake(ONE_MINUTE_MILLIS)
            scheduler.cancelWake()
            states()
        }

        assertThat(statesDuringRun)
            .containsExactlyInAnyOrder(WorkInfo.State.RUNNING, WorkInfo.State.CANCELLED)
    }

    @Test
    fun runNowReplacesAPendingRequest() {
        scheduler.scheduleWake(ONE_MINUTE_MILLIS)
        val pending = requests().single()

        scheduler.runNow()

        assertForegroundRequestRunsNow(pending.id)
    }

    @Test
    fun runNowReplacesARetryingRequest() {
        val id = scheduleRetryingWake()

        scheduler.runNow()

        assertForegroundRequestRunsNow(id)
    }

    @Test
    fun runNowDoesNothingWhileARequestRuns() {
        val statesDuringRun = duringRun {
            scheduler.runNow()
            states()
        }

        assertThat(statesDuringRun).containsExactly(WorkInfo.State.RUNNING)
    }

    @Test
    fun runNowExpeditesWhereSupported() {
        scheduler.runNow()
        val request = requests().single()

        runWake(request.id) { Result.success() }

        assertThat(ranExpedited(request.id)).isEqualTo(OS.supportsExpeditedJobs())
    }

    private fun assertForegroundRequestRunsNow(replacedId: UUID) {
        val request = requests().single()
        assertThat(request.id).isNotEqualTo(replacedId)
        assertThat(request.state).isEqualTo(WorkInfo.State.ENQUEUED)
        assertThat(request.nextScheduleTimeMillis).isEqualTo(clock.now)
        assertThat(request.tags).contains(RUN_NOW_TAG)
    }

    private fun ranExpedited(id: UUID): Boolean {
        return requireNotNull(runInputs[id]).getBoolean(EXPEDITED_KEY, false)
    }

    /**
     * Schedule a wake whose run asks for a retry, and return its id.
     */
    private fun scheduleRetryingWake(): UUID {
        scheduler.scheduleWake(0)
        val id = requests().single().id

        runWake(id) { Result.retry() }

        return id
    }

    /**
     * Schedule a wake and run body inside its run, returning what body returned once the run
     * succeeds.
     */
    private fun <T : Any> duringRun(body: () -> T): T {
        scheduler.scheduleWake(0)
        val result = AtomicReference<T>()

        runWake(requests().single().id) {
            result.set(body())
            Result.success()
        }

        return requireNotNull(result.get()) { "The run did not finish its body" }
    }

    /**
     * Run the request, with onRun as the worker's body, and wait until WorkManager records its
     * result.
     */
    private fun runWake(id: UUID, onRun: () -> Result) {
        this.onRun = onRun
        driver.setAllConstraintsMet(id)
        driver.setInitialDelayMet(id)

        val deadline = SystemClock.elapsedRealtime() + RUN_TIMEOUT_MILLIS
        while (id !in finishedRuns || workInfo(id).state == WorkInfo.State.RUNNING) {
            check(SystemClock.elapsedRealtime() < deadline) { "Request $id did not run" }
            Thread.sleep(10)
        }
    }

    private fun requests(): List<WorkInfo> {
        return workManager
            .getWorkInfosForUniqueWork(BACKGROUND_ACTIONS_WORK_NAME)
            .get()
    }

    private fun states(): List<WorkInfo.State> {
        return requests().map { it.state }
    }

    private fun requestIn(state: WorkInfo.State): WorkInfo {
        return requests().single { it.state == state }
    }

    private fun workInfo(id: UUID): WorkInfo {
        val workInfo = workManager
            .getWorkInfoById(id)
            .get()

        return requireNotNull(workInfo)
    }
}
