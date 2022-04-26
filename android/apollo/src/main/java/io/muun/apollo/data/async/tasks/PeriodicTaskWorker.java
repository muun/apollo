package io.muun.apollo.data.async.tasks;

import io.muun.apollo.data.os.execution.ExecutionTransformerFactory;
import io.muun.apollo.domain.action.UserActions;
import io.muun.apollo.domain.errors.NoStackTraceException;
import io.muun.apollo.domain.errors.PeriodicTaskError;
import io.muun.common.utils.Preconditions;

import android.content.Context;
import android.os.SystemClock;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import timber.log.Timber;

public class PeriodicTaskWorker extends Worker {

    public static final String TASK_TYPE_KEY = "taskType";

    private final TaskDispatcher taskDispatcher;

    private final UserActions userActions;

    private final ExecutionTransformerFactory transformerFactory;

    /**
     * Constructor. This is now called from background (WorkManager handles it) so dependency
     * injection is handled by a WorkFactory. See MuunWorkerFactory.
     */
    public PeriodicTaskWorker(@NonNull Context context,
                              @NonNull WorkerParameters workerParams,
                              TaskDispatcher taskDispatcher,
                              UserActions userActions,
                              ExecutionTransformerFactory transformerFactory) {
        super(context, workerParams);
        this.taskDispatcher = taskDispatcher;
        this.userActions = userActions;
        this.transformerFactory = transformerFactory;

        Timber.d("Starting PeriodicTaskService");
    }

    @Override
    @NonNull
    public Result doWork() {

        if (!userActions.isLoggedIn()) {
            return Result.success();
        }

        // The scheduler will hold a PowerManager.WakeLock for your service, however after three
        // minutes of execution if your task has not returned it will be considered to have timed
        // out, and the wakelock will be released.

        final String type = Preconditions.checkNotNull(getInputData().getString(TASK_TYPE_KEY));

        Timber.d("Running periodic task of type %s", type);

        final long startMs = SystemClock.elapsedRealtime();

        try {

            taskDispatcher.dispatch(type)
                    .doOnError(throwable -> {

                        if (throwable.getStackTrace() == null) {
                            fillInStackTrace(throwable);
                        }

                        if (throwable.getStackTrace() == null) {

                            final String message = String.format(
                                    "Exception of type %s with no stacktrace, while running a "
                                            + "periodic task of type %s. Message: %s.",
                                    throwable.getClass().getCanonicalName(),
                                    type,
                                    throwable.getMessage()
                            );

                            throwable = new NoStackTraceException(message);
                        }

                        Timber.e(throwable);
                    })
                    .compose(transformerFactory.getAsyncExecutor())
                    .toBlocking()
                    .subscribe();

        } catch (RuntimeException error) {
            Timber.e(new PeriodicTaskError(type, secondsSince(startMs), error));
            return Result.retry();
        }

        Timber.d("Success after " + secondsSince(startMs) + "s on periodic %s", type);
        return Result.success();
    }

    private void fillInStackTrace(Throwable throwable) {
        // Keep this in its own method, in order to have an understandable stack-trace.
        throwable.fillInStackTrace();
    }

    @Override
    public void onStopped() {
        super.onStopped();
        Timber.d("Stopping PeriodicTaskWorker");
    }

    private long secondsSince(long startingRealtime) {
        return (SystemClock.elapsedRealtime() - startingRealtime) / 1000;
    }
}
