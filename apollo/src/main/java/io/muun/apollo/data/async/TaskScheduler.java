package io.muun.apollo.data.async;

import io.muun.apollo.data.logging.Logger;

import android.content.Context;
import android.os.Bundle;
import com.google.android.gms.gcm.GcmNetworkManager;
import com.google.android.gms.gcm.PeriodicTask;
import org.threeten.bp.Duration;
import rx.Observable;
import rx.schedulers.Schedulers;

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

    private final Context context;

    /**
     * Create a task scheduler.
     */
    @Inject
    public TaskScheduler(Context context) {

        this.context = context;
    }

    private void scheduleDelayedPeriodicTask(@NotNull String type, @Nonnegative long periodLength) {
        Observable
                .fromCallable(() -> {
                    schedulePeriodicTaskLater(type, periodLength);
                    return null;
                })
                .delay(5, TimeUnit.SECONDS) // see below
                .subscribeOn(Schedulers.computation()) // TODO pick a better scheduler?
                .subscribe();
    }

    /**
     * Schedule an async network-bound periodic task that will run every {periodLength} seconds. If
     * the task fails (ie. throws), it won't be retried.
     */
    private void schedulePeriodicTaskLater(@NotNull String type, @Nonnegative long periodLength) {
        // NOTE: this delay is a potential fix for a frequent IntegrityCheck failure, occuring
        // after opening the app from a notification. I think the problem is that the task is
        // executed on opening the app, before the balance is updated, and the check fails. Somehow.
        // I'm testing this hypothesis, really, the bug is hard to reproduce.
        final Bundle data = new Bundle();

        data.putString(PeriodicTaskService.TASK_TYPE_KEY, type);

        final PeriodicTask task = new PeriodicTask.Builder()
                .setService(PeriodicTaskService.class)
                .setPersisted(true)
                .setPeriod(periodLength)
                .setFlex(periodLength)
                .setTag(type)
                .setUpdateCurrent(true)
                .setRequiredNetwork(com.google.android.gms.gcm.Task.NETWORK_STATE_CONNECTED)
                .setExtras(data)
                .build();

        GcmNetworkManager.getInstance(context).schedule(task);
        Logger.info("Scheduled a periodic task of type %s", type);
    }

    private static final long PULL_NOTIFICATIONS_PERIOD = Duration.ofHours(3).getSeconds();
    private static final long FALLBACK_SYNC_PERIOD = Duration.ofHours(6).getSeconds();
    private static final long INTEGRITY_CHECK_PERIOD = Duration.ofDays(1).getSeconds();

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
        GcmNetworkManager.getInstance(context).cancelAllTasks(PeriodicTaskService.class);
    }
}
