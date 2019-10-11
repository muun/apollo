package io.muun.apollo.data.async.tasks;

import io.muun.apollo.data.logging.Logger;

import android.content.Context;
import android.os.Bundle;
import androidx.annotation.NonNull;
import com.firebase.jobdispatcher.Constraint;
import com.firebase.jobdispatcher.FirebaseJobDispatcher;
import com.firebase.jobdispatcher.GooglePlayDriver;
import com.firebase.jobdispatcher.Job;
import com.firebase.jobdispatcher.Lifetime;
import com.firebase.jobdispatcher.RetryStrategy;
import com.firebase.jobdispatcher.Trigger;
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

    private void scheduleDelayedPeriodicTask(@NotNull String type, @Nonnegative int periodLength) {
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
    private void schedulePeriodicTaskLater(@NotNull String type, @Nonnegative int periodLength) {
        // NOTE: this delay is a potential fix for a frequent IntegrityCheck failure, occuring
        // after opening the app from a notification. I think the problem is that the task is
        // executed on opening the app, before the balance is updated, and the check fails. Somehow.
        // I'm testing this hypothesis, really, the bug is hard to reproduce.
        final Bundle data = new Bundle();

        data.putString(PeriodicTaskService.TASK_TYPE_KEY, type);

        final FirebaseJobDispatcher dispatcher = getDispatcher();


        final Job task = dispatcher.newJobBuilder()
                .setService(PeriodicTaskService.class)
                .setTag(type)
                .setLifetime(Lifetime.FOREVER)
                .setReplaceCurrent(true)
                .setRetryStrategy(RetryStrategy.DEFAULT_EXPONENTIAL)
                .setConstraints(Constraint.ON_ANY_NETWORK)
                .setRecurring(true)
                .setTrigger(Trigger.executionWindow(periodLength, periodLength))
                .setExtras(data)
                .build();

        dispatcher.schedule(task);
        Logger.info("Scheduled a periodic task of type %s", type);
    }

    @NonNull
    private FirebaseJobDispatcher getDispatcher() {
        final GooglePlayDriver googlePlayDriver = new GooglePlayDriver(context);
        return new FirebaseJobDispatcher(googlePlayDriver);
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
        getDispatcher().cancelAll();
    }
}
