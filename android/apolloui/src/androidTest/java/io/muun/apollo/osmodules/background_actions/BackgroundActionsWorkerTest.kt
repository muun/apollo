package io.muun.apollo.osmodules.background_actions

import android.content.Context
import android.os.CancellationSignal
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.ListenableWorker
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestWorkerBuilder
import androidx.work.workDataOf
import io.grpc.Status
import io.muun.apollo.data.os.ProcessLifecycleAppStateProvider
import io.muun.apollo.domain.analytics.Analytics
import io.muun.apollo.domain.errors.PeriodicTaskError
import io.muun.apollo.domain.libwallet.errors.BackgroundActionsRunInterruptedError
import io.muun.apollo.domain.libwallet.errors.BackgroundActionsRunInterruptedError.Reason
import io.muun.apollo.domain.libwallet.errors.LibwalletGrpcError
import io.muun.apollo.domain.model.background_actions.BackgroundActionsRunOrigin
import io.muun.apollo.domain.model.background_actions.ExecutionContext
import io.muun.apollo.presentation.app.ApolloApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.threeten.bp.Duration
import timber.log.Timber
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

private const val SLEEP_MILLIS = 200L

private val BACKGROUND_TIMEOUT = Duration.ofMinutes(2)
private val FOREGROUND_TIMEOUT = Duration.ofMinutes(3)
private val FOREGROUND_EXPEDITED_TIMEOUT = Duration.ofMinutes(2)
private val BACKGROUND_EXPEDITED_TIMEOUT = Duration.ofMinutes(1)

// Time the worker may take between creation and the call.
private val CALL_SLACK = Duration.ofSeconds(1)

@RunWith(AndroidJUnit4::class)
class BackgroundActionsWorkerTest {

    private class TestOwner : LifecycleOwner {
        override val lifecycle = LifecycleRegistry.createUnsafe(this)
    }

    private lateinit var context: Context
    private lateinit var analytics: Analytics

    private val executor = Executors.newSingleThreadExecutor()

    private val calls = mutableListOf<Pair<ExecutionContext, Duration>>()

    private val owner = TestOwner()

    // Read by the worker when its run starts.
    private val appStateProvider = ProcessLifecycleAppStateProvider(owner.lifecycle)

    private val reportedErrors = CopyOnWriteArrayList<Throwable>()

    private val reportingTree = object : Timber.Tree() {
        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            if (priority == Log.ERROR && t != null) {
                reportedErrors.add(t)
            }
        }
    }

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        analytics = (context.applicationContext as ApolloApplication).dataComponent.analytics()
        Timber.plant(reportingTree)
    }

    @After
    fun tearDown() {
        Timber.uproot(reportingTree)
        executor.shutdownNow()
    }

    @Test
    fun backgroundRegularRunGetsTwoMinutesMinusTheTimeSinceCreation() {
        enterBackground()
        val worker = buildWorker()
        Thread.sleep(SLEEP_MILLIS)

        assertThat(worker.startWork().get()).isEqualTo(Result.success())

        assertCall(
            ExecutionContext.BACKGROUND,
            BACKGROUND_TIMEOUT.minus(CALL_SLACK),
            BACKGROUND_TIMEOUT.minusMillis(SLEEP_MILLIS)
        )
    }

    @Test
    fun foregroundRegularRunGetsThreeMinutes() {
        enterForeground()
        val worker = buildWorker()

        assertThat(worker.startWork().get()).isEqualTo(Result.success())

        assertCall(ExecutionContext.FOREGROUND, FOREGROUND_TIMEOUT)
    }

    @Test
    fun foregroundExpeditedRunGetsTwoMinutes() {
        enterForeground()
        val worker = buildWorker(expedited = true)

        assertThat(worker.startWork().get()).isEqualTo(Result.success())

        assertCall(ExecutionContext.FOREGROUND, FOREGROUND_EXPEDITED_TIMEOUT)
    }

    @Test
    fun backgroundExpeditedRunGetsOneMinute() {
        enterBackground()
        val worker = buildWorker(expedited = true)

        assertThat(worker.startWork().get()).isEqualTo(Result.success())

        assertCall(ExecutionContext.BACKGROUND, BACKGROUND_EXPEDITED_TIMEOUT)
    }

    @Test
    fun expeditedRetryGetsTheRegularTimeout() {
        enterForeground()
        val worker = buildWorker(expedited = true, runAttemptCount = 1)

        assertThat(worker.startWork().get()).isEqualTo(Result.success())

        assertCall(ExecutionContext.FOREGROUND, FOREGROUND_TIMEOUT)
    }

    @Test
    fun interruptedCallsRetryWithoutReporting() {
        val errors = listOf(
            BackgroundActionsRunInterruptedError(Reason.CANCELLED, RuntimeException()),
            BackgroundActionsRunInterruptedError(Reason.TIMEOUT, RuntimeException()),
        )

        for (error in errors) {
            val worker = buildWorker {
                throw error
            }

            assertThat(worker.startWork().get()).isEqualTo(Result.retry())
            assertThat(reportsOf(error)).isEmpty()
        }
    }

    @Test
    fun failedCallsRetryAndReport() {
        val errors = listOf(
            LibwalletGrpcError(Status.INTERNAL.asRuntimeException()),
            IllegalStateException("boom"),
        )

        for (error in errors) {
            val worker = buildWorker {
                throw error
            }

            assertThat(worker.startWork().get()).isEqualTo(Result.retry())
            assertThat(reportsOf(error)).hasSize(1)
        }
    }

    @Test
    fun stoppingCancelsTheCall() {
        val started = CountDownLatch(1)
        val worker = buildWorker { cancellation ->
            started.countDown()
            while (!cancellation.isCanceled) {
                Thread.sleep(10)
            }
            throw BackgroundActionsRunInterruptedError(Reason.CANCELLED, RuntimeException())
        }

        val result = worker.startWork()
        assertThat(started.await(5, TimeUnit.SECONDS)).isTrue

        worker.stop(0)

        assertThat(result.get()).isEqualTo(Result.retry())
    }

    private fun buildWorker(
        expedited: Boolean = false,
        runAttemptCount: Int = 0,
        runDueBackgroundActions: (CancellationSignal) -> Unit = {},
    ): BackgroundActionsWorker {
        val factory = object : WorkerFactory() {
            override fun createWorker(
                appContext: Context,
                workerClassName: String,
                workerParameters: WorkerParameters,
            ): ListenableWorker {
                return BackgroundActionsWorker(
                    appContext,
                    workerParameters,
                    { calledContext, timeout, cancellation ->
                        calls.add(calledContext to timeout)
                        runDueBackgroundActions(cancellation)
                    },
                    appStateProvider,
                    analytics
                )
            }
        }

        return TestWorkerBuilder<BackgroundActionsWorker>(context, executor)
            .setWorkerFactory(factory)
            .setInputData(
                workDataOf(
                    EXPEDITED_KEY to expedited,
                    ORIGIN_KEY to BackgroundActionsRunOrigin.RUN_NOW.name
                )
            )
            .setRunAttemptCount(runAttemptCount)
            .build()
    }

    private fun reportsOf(error: Throwable): List<PeriodicTaskError> {
        return reportedErrors.filterIsInstance<PeriodicTaskError>().filter { it.cause === error }
    }

    private fun assertCall(executionContext: ExecutionContext, timeout: Duration) {
        assertCall(executionContext, timeout.minus(CALL_SLACK), timeout)
    }

    private fun assertCall(
        executionContext: ExecutionContext,
        minTimeout: Duration,
        maxTimeout: Duration,
    ) {
        assertThat(calls).hasSize(1)

        val (calledContext, timeout) = calls.single()
        assertThat(calledContext).isEqualTo(executionContext)
        assertThat(timeout.toMillis()).isBetween(minTimeout.toMillis(), maxTimeout.toMillis())
    }

    private fun enterForeground() {
        owner.lifecycle.currentState = Lifecycle.State.STARTED
    }

    private fun enterBackground() {
        owner.lifecycle.currentState = Lifecycle.State.CREATED
    }
}
