package io.muun.apollo.data.async.tasks;

import io.muun.apollo.data.os.execution.ExecutionTransformerFactory;
import io.muun.apollo.domain.action.UserActions;
import io.muun.apollo.domain.errors.NoStackTraceException;
import io.muun.apollo.domain.errors.PeriodicTaskError;
import io.muun.apollo.external.DataComponentProvider;

import android.os.Bundle;
import android.os.SystemClock;
import com.firebase.jobdispatcher.JobParameters;
import com.firebase.jobdispatcher.JobService;
import com.firebase.jobdispatcher.SimpleJobService;
import timber.log.Timber;

import javax.inject.Inject;

public class PeriodicTaskService extends SimpleJobService {

    public static final String TASK_TYPE_KEY = "taskType";

    @Inject
    TaskDispatcher taskDispatcher;

    @Inject
    UserActions userActions;

    @Inject
    ExecutionTransformerFactory transformerFactory;

    @Override
    public void onCreate() {
        super.onCreate();

        final DataComponentProvider provider = (DataComponentProvider) getApplication();
        provider.getDataComponent().inject(this);

        Timber.d("Starting PeriodicTaskService");
    }

    @Override
    public int onRunJob(JobParameters taskParams) {

        if (!userActions.isLoggedIn()) {
            return JobService.RESULT_SUCCESS;
        }

        // The scheduler will hold a PowerManager.WakeLock for your service, however after three
        // minutes of execution if your task has not returned it will be considered to have timed
        // out, and the wakelock will be released.

        final Bundle params = taskParams.getExtras();
        final String type = params.getString(TASK_TYPE_KEY);

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
                                "Exception of type %s with no stacktrace, while running a periodic "
                                        + "task of type %s. Message: %s.",
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
            return JobService.RESULT_FAIL_RETRY;
        }

        Timber.d("Success after " + secondsSince(startMs) + "s on periodic %s", type);
        return JobService.RESULT_SUCCESS;
    }

    private void fillInStackTrace(Throwable throwable) {
        // Keep this in its own method, in order to have an understandable stack-trace.
        throwable.fillInStackTrace();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Timber.d("Destroying PeriodicTaskService");
    }

    private long secondsSince(long startingRealtime) {
        return (SystemClock.elapsedRealtime() - startingRealtime) / 1000;
    }
}
