package io.muun.apollo.osmodules.background_actions

import android.content.Context
import android.os.CancellationSignal
import android.os.SystemClock
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.Worker
import androidx.work.WorkerParameters
import io.muun.apollo.data.async.tasks.mapStopReason
import io.muun.apollo.data.async.tasks.stopReasonOrNull
import io.muun.apollo.data.os.AppStateProvider
import io.muun.apollo.data.os.isForeground
import io.muun.apollo.domain.analytics.Analytics
import io.muun.apollo.domain.analytics.AnalyticsEvent.BACKGROUND_ACTIONS_RUN_FAILURE_REASON
import io.muun.apollo.domain.analytics.AnalyticsEvent.E_BACKGROUND_ACTIONS_RUN_FAILURE
import io.muun.apollo.domain.analytics.AnalyticsEvent.E_BACKGROUND_ACTIONS_RUN_STARTED
import io.muun.apollo.domain.analytics.AnalyticsEvent.E_BACKGROUND_ACTIONS_RUN_SUCCESS
import io.muun.apollo.domain.errors.PeriodicTaskError
import io.muun.apollo.domain.libwallet.errors.BackgroundActionsRunInterruptedError
import io.muun.apollo.domain.libwallet.errors.BackgroundActionsRunInterruptedError.Reason
import io.muun.apollo.domain.model.background_actions.BackgroundActionsRunOrigin
import io.muun.apollo.domain.model.background_actions.ExecutionContext
import org.threeten.bp.Duration
import timber.log.Timber
import java.util.UUID
import java.util.concurrent.TimeUnit

private const val WORKER_TYPE = "background_actions"

internal const val EXPEDITED_KEY = "expedited"

internal const val ORIGIN_KEY = "origin"

/**
 * Runs the due background actions in libwallet.
 * Requested by BackgroundActionsScheduler.
 * Built by MuunWorkerFactory.
 */
class BackgroundActionsWorker(
    context: Context,
    params: WorkerParameters,
    private val runDueBackgroundActions: (ExecutionContext, Duration, CancellationSignal) -> Unit,
    private val appStateProvider: AppStateProvider,
    private val analytics: Analytics,
) : Worker(context, params) {

    companion object {
        fun buildRequest(
            origin: BackgroundActionsRunOrigin,
            delayMillis: Long = 0,
            id: UUID? = null,
            tag: String? = null,
            expedited: Boolean = false,
        ): OneTimeWorkRequest {
            require(!expedited || delayMillis == 0L) {
                "Expedited work cannot be delayed"
            }

            val builder = OneTimeWorkRequestBuilder<BackgroundActionsWorker>()
                .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)

            if (id != null) {
                builder.setId(id)
            }

            if (tag != null) {
                builder.addTag(tag)
            }

            val inputData = Data.Builder()
                .putString(ORIGIN_KEY, origin.name)

            if (expedited) {
                builder.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                inputData.putBoolean(EXPEDITED_KEY, true)
            }

            return builder
                .setInputData(inputData.build())
                .build()
        }
    }

    private val startMillis = SystemClock.elapsedRealtime()

    private val cancellation = CancellationSignal()

    private val executionContext by lazy {
        if (appStateProvider.isForeground()) {
            ExecutionContext.FOREGROUND
        } else {
            ExecutionContext.BACKGROUND
        }
    }

    // WorkManager never expedites a retry.
    private val expedited = inputData.getBoolean(EXPEDITED_KEY, false) && runAttemptCount == 0

    private val origin =
        BackgroundActionsRunOrigin.valueOf(requireNotNull(inputData.getString(ORIGIN_KEY)))

    override fun doWork(): Result {
        try {
            reportStarted()

            val budget = ExecutionContextBudget.of(executionContext)
            val timeout = budget.timeout(expedited).minusMillis(millisSinceStart())

            runDueBackgroundActions(executionContext, timeout, cancellation)

            reportSuccess()
            return Result.success()

        } catch (e: BackgroundActionsRunInterruptedError) {
            Timber.i(e)
            reportInterruption(e.reason)
            return Result.retry()

        } catch (e: Exception) {
            Timber.e(PeriodicTaskError(WORKER_TYPE, millisSinceStart(), e))
            reportFailure(e)
            return Result.retry()
        }
    }

    override fun onStopped() {
        super.onStopped()

        cancellation.cancel()
    }

    private fun reportStarted() =
        analytics.report(E_BACKGROUND_ACTIONS_RUN_STARTED(
            id.toString(),
            runAttemptCount,
            executionContext,
            origin,
            expedited
        ))

    private fun reportSuccess() =
        analytics.report(E_BACKGROUND_ACTIONS_RUN_SUCCESS(
            id.toString(),
            runAttemptCount,
            executionContext,
            origin,
            expedited,
            millisSinceStart()
        ))

    private fun reportInterruption(reason: Reason) {
        val failureReason = when (reason) {
            Reason.CANCELLED -> BACKGROUND_ACTIONS_RUN_FAILURE_REASON.STOPPED
            Reason.TIMEOUT -> BACKGROUND_ACTIONS_RUN_FAILURE_REASON.TIMEOUT
        }

        val stopReason = when (failureReason) {
            BACKGROUND_ACTIONS_RUN_FAILURE_REASON.STOPPED -> mapStopReason(stopReasonOrNull())
            else -> null
        }

        analytics.report(E_BACKGROUND_ACTIONS_RUN_FAILURE(
            id.toString(),
            runAttemptCount,
            executionContext,
            origin,
            expedited,
            millisSinceStart(),
            failureReason,
            stopReason
        ))
    }

    private fun reportFailure(error: Throwable) =
        analytics.report(E_BACKGROUND_ACTIONS_RUN_FAILURE(
            id.toString(),
            runAttemptCount,
            executionContext,
            origin,
            expedited,
            millisSinceStart(),
            reason = BACKGROUND_ACTIONS_RUN_FAILURE_REASON.ERROR,
            error = error
        ))

    private fun millisSinceStart(): Long {
        return SystemClock.elapsedRealtime() - startMillis
    }
}

/**
 * Time budget of a background actions run per execution context. Derived from JobScheduler's quota
 * accounting per standby bucket.
 */
private enum class ExecutionContextBudget(
    val regularTimeout: Duration,
    val expeditedTimeout: Duration,
) {
    FOREGROUND(
        // The time run on foreground is quota exempt. Assuming the app stays on top for a
        // minute, a regular run gets a minute more than a background one.
        regularTimeout = Duration.ofMinutes(3),
        // Expedited jobs spend no quota until a minute after the app leaves the foreground, so
        // a run started on top spends at most one minute.
        expeditedTimeout = Duration.ofMinutes(2),
    ),

    BACKGROUND(
        // A fifth of the FREQUENT runtime quota per standby bucket window.
        regularTimeout = Duration.ofMinutes(2),
        // An expedited run started off the foreground spends expedited quota from the start, so
        // it also spends at most one minute.
        expeditedTimeout = Duration.ofMinutes(1),
    );

    fun timeout(expedited: Boolean): Duration {
        return if (expedited) {
            expeditedTimeout
        } else {
            regularTimeout
        }
    }

    companion object {
        fun of(executionContext: ExecutionContext): ExecutionContextBudget {
            return when (executionContext) {
                ExecutionContext.FOREGROUND -> FOREGROUND
                ExecutionContext.BACKGROUND -> BACKGROUND
            }
        }
    }
}
