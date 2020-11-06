package io.muun.apollo.data.async.tasks;

import android.content.Context;
import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import org.threeten.bp.Duration;
import timber.log.Timber;

import java.util.concurrent.TimeUnit;
import javax.annotation.Nonnegative;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.validation.constraints.NotNull;

/**
 * The task scheduler lets you create task from the application, that will run in a service.
 *
 * <p>A task is represented by a task type (string) and a task payload (parcelable object).
 *
 * <p>To add a new task type, call addTaskType from the task dispatcher constructor.
 */
@Singleton
public class TaskScheduler {

    // WorkManager imposes a min interval of 15 min (consider it when reducing these for testing)
    private static final int PULL_NOTIFICATIONS_PERIOD = (int) Duration.ofHours(3).getSeconds();
    private static final int FALLBACK_SYNC_PERIOD = (int) Duration.ofHours(6).getSeconds();
    private static final int INTEGRITY_CHECK_PERIOD = (int) Duration.ofDays(1).getSeconds();

    private final Context context;

    /**
     * Create a task scheduler.
     */
    @Inject
    public TaskScheduler(Context context) {

        this.context = context;
    }

    /**
     * Schedule an async network-bound periodic task that will run every {repeatIntervalInSecs}.
     */
    private void scheduleDelayedPeriodicTask(@NotNull String type,
                                             @Nonnegative int repeatIntervalInSecs) {

        // NOTE: this delay is a potential fix for a frequent IntegrityCheck failure, occurring
        // after opening the app from a notification. I think the problem is that the task is
        // executed on opening the app, before the balance is updated, and the check fails. Somehow.
        // I'm testing this hypothesis, really, the bug is hard to reproduce.
        // TODO with WorkManager this could now be solved via chaining of dependent tasks/workers

        final Data input = new Data.Builder()
                .putString(PeriodicTaskWorker.TASK_TYPE_KEY, type)
                .build();

        final Constraints constraints = new Constraints.Builder()
                // The Worker needs Network connectivity
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        // WorkManager’s jobs are always persisted across device reboot automatically.
        // Expected schedule is: [30s, 60s, 120s, 240s, ..., 18000s] (capped at 5 hours)
        final PeriodicWorkRequest workRequest = new PeriodicWorkRequest
                .Builder(PeriodicTaskWorker.class, repeatIntervalInSecs, TimeUnit.SECONDS)
                .setInputData(input)
                .setConstraints(constraints)
                .setInitialDelay(5, TimeUnit.SECONDS)   // See above
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build();

        getWorkManager()
                .enqueueUniquePeriodicWork(type, ExistingPeriodicWorkPolicy.REPLACE, workRequest);

        Timber.d("Scheduled a periodic task of type %s", type);
    }

    /**
     * Schedule all the periodic tasks that should be always running if the user is logged in.
     */
    public void scheduleAllTasks() {
        // Notifications usually arrive via GCM, and are manually pulled when the application is
        // brought to the foreground. Still, we check them every now and then:
        scheduleDelayedPeriodicTask("pullNotifications", PULL_NOTIFICATIONS_PERIOD);

        // Following data is usually synchronized on demand. Several Presenters do this when set up.
        // If the user doesn't use the app in a long time, however, these values can go severly
        // stale. This long timer keeps the data somewhat fresh:
        scheduleDelayedPeriodicTask("syncPhoneContacts", FALLBACK_SYNC_PERIOD);
        scheduleDelayedPeriodicTask("syncRealTimeData", FALLBACK_SYNC_PERIOD);
        scheduleDelayedPeriodicTask("syncExternalAddressesIndexes", FALLBACK_SYNC_PERIOD);

        // Past bugs have introduced integrity errors that we had no way of detecting. This periodic
        // check with Houston takes care of that:
        scheduleDelayedPeriodicTask("checkIntegrity", INTEGRITY_CHECK_PERIOD);
    }

    /**
     * Cancel all future executions of scheduled tasks.
     */
    public void unscheduleAllTasks() {
        getWorkManager().cancelAllWork();
    }

    private WorkManager getWorkManager() {
        return WorkManager.getInstance(context);
    }
}
