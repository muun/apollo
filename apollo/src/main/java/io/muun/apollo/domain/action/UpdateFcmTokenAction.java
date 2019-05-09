package io.muun.apollo.domain.action;

import io.muun.apollo.data.logging.Logger;
import io.muun.apollo.data.net.HoustonClient;
import io.muun.apollo.data.os.execution.ExecutionTransformerFactory;
import io.muun.apollo.data.preferences.UserRepository;
import io.muun.apollo.domain.action.base.BaseAsyncAction1;

import rx.Observable;

import javax.inject.Inject;

public class UpdateFcmTokenAction extends BaseAsyncAction1<String, Void> {

    private final HoustonClient houstonClient;
    private final UserRepository userRepository;
    private final ExecutionTransformerFactory executionTransformerFactory;
    private final NotificationActions notificationActions;

    /**
     * constructor.
     */
    @Inject
    public UpdateFcmTokenAction(HoustonClient houstonClient,
                                UserRepository userRepository,
                                ExecutionTransformerFactory executionTransformerFactory,
                                NotificationActions notificationActions) {
        this.houstonClient = houstonClient;
        this.userRepository = userRepository;
        this.executionTransformerFactory = executionTransformerFactory;
        this.notificationActions = notificationActions;
    }

    /**
     * @param token from firebase.
     * @return an observable action.
     */
    @Override
    public Observable<Void> action(String token) {

        userRepository.storeFcmToken(token);

        return houstonClient.updateFcmToken(token)
                .compose(executionTransformerFactory.getAsyncExecutor())
                .flatMap(ignore -> notificationActions.pullNotifications())
                .doOnError(error -> Logger.error(error, "Error while trying to refresh FCM token"));
    }
}
