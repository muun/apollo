package io.muun.apollo.data.async;

import io.muun.apollo.data.db.DaoManager;
import io.muun.apollo.data.di.DataComponentProvider;
import io.muun.apollo.data.logging.Logger;
import io.muun.apollo.data.os.execution.ExecutionTransformerFactory;
import io.muun.apollo.domain.action.SigninActions;
import io.muun.apollo.domain.errors.NoStackTraceException;

import android.content.Intent;
import android.os.Bundle;
import com.google.android.gms.gcm.GcmNetworkManager;
import com.google.android.gms.gcm.GcmTaskService;
import com.google.android.gms.gcm.TaskParams;

import javax.inject.Inject;

public class PeriodicTaskService extends GcmTaskService {

    public static final String TASK_TYPE_KEY = "taskType";

    @Inject
    DaoManager daoManager;

    @Inject
    TaskDispatcher taskDispatcher;

    @Inject
    TaskScheduler taskScheduler;

    @Inject
    SigninActions signinActions;

    @Inject
    ExecutionTransformerFactory transformerFactory;

    @Override
    public void onCreate() {
        super.onCreate();
        final DataComponentProvider provider = (DataComponentProvider) getApplication();
        provider.getDataComponent().inject(this);
        Logger.info("Starting PeriodicTaskService");
    }

    @Override
    public void onInitializeTasks() {

        // Called when the app or gcm is updated or reinstalled, to re-schedule all periodic tasks.

        if (signinActions.isSignedIn()) {
            taskScheduler.scheduleAllTasks();
        }
    }

    @Override
    public int onRunTask(TaskParams taskParams) {

        if (!signinActions.isSignedIn()) {
            return GcmNetworkManager.RESULT_SUCCESS;
        }

        // The scheduler will hold a PowerManager.WakeLock for your service, however after three
        // minutes of execution if your task has not returned it will be considered to have timed
        // out, and the wakelock will be released.

        final Bundle params = taskParams.getExtras();
        final String type = params.getString(TASK_TYPE_KEY);

        Logger.info("Running periodic task of type %s", type);

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

                    Logger.error(throwable);
                })
                .compose(transformerFactory.getAsyncExecutor())
                .toBlocking()
                .subscribe();

        } catch (RuntimeException error) {
            Logger.error(error, "Error while running periodic task of type: %s", type);
            return GcmNetworkManager.RESULT_FAILURE;
        }

        Logger.info("Successfully run a periodic task of type: %s", type);
        return GcmNetworkManager.RESULT_SUCCESS;
    }

    private void fillInStackTrace(Throwable throwable) {
        // Keep this in its own method, in order to have an understandable stack-trace.
        throwable.fillInStackTrace();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        if (intent == null) {
            // GcmTaskService doesn't check for null intent
            // https://github.com/google/gcm/issues/87
            stopSelf();
            return START_NOT_STICKY;
        }

        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Logger.info("Destroying PeriodicTaskService");
    }
}
