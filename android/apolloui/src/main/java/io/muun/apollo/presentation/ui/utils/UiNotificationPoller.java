package io.muun.apollo.presentation.ui.utils;

import io.muun.apollo.data.external.Globals;
import io.muun.apollo.data.os.execution.ExecutionTransformerFactory;
import io.muun.apollo.domain.action.NotificationActions;

import rx.Observable;
import rx.Subscription;
import timber.log.Timber;

import java.util.concurrent.TimeUnit;
import javax.inject.Inject;

public class UiNotificationPoller {

    private static final boolean CI = Globals.INSTANCE.getOldBuildType().equals("regtestDebug");
    private static final int POLL_INTERVAL_IN_SECS = CI ? 5 : 2;

    private final NotificationActions notificationActions;
    private final ExecutionTransformerFactory transformerFactory;

    private Subscription subscription;

    /**
     * Constructor.
     */
    @Inject
    public UiNotificationPoller(NotificationActions notificationActions,
                                ExecutionTransformerFactory transformerFactory) {

        this.notificationActions = notificationActions;
        this.transformerFactory = transformerFactory;
    }

    /**
     * Start polling notifications every `POLL_INTERVAL_IN_SECS` seconds, in background. Note that
     * the first call will happen immediately, rather than wait for the first interval.
     */
    public void start() {
        if (subscription == null || subscription.isUnsubscribed()) {
            subscription = subscribeToNotificationPolling();
        }
    }

    /**
     * Stop polling.
     */
    public void stop() {
        if (subscription != null && !subscription.isUnsubscribed()) {
            subscription.unsubscribe();
            subscription = null;
        }
    }

    private Subscription subscribeToNotificationPolling() {
        return Observable
                .interval(POLL_INTERVAL_IN_SECS, TimeUnit.SECONDS)
                .startWith(0L)
                .onBackpressureLatest()
                .concatMap(ignored -> safelyPullNotifications())
                .compose(transformerFactory.getAsyncExecutor())
                .subscribe();
    }

    private Observable<Void> safelyPullNotifications() {
        return notificationActions.pullNotifications()
                .onErrorReturn(error -> {
                    Timber.e(error);
                    return null;
                });
    }

}
