package io.muun.apollo.domain.action;

import io.muun.apollo.data.net.HoustonClient;
import io.muun.apollo.data.os.execution.ExecutionTransformerFactory;
import io.muun.apollo.data.preferences.AuthRepository;
import io.muun.apollo.data.preferences.UserRepository;
import io.muun.apollo.domain.action.base.BaseAsyncAction1;

import rx.Observable;
import timber.log.Timber;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class UpdateFcmTokenAction extends BaseAsyncAction1<String, Void> {

    private final HoustonClient houstonClient;
    private final UserRepository userRepository;
    private final AuthRepository authRepository;
    private final ExecutionTransformerFactory executionTransformerFactory;
    private final NotificationActions notificationActions;

    /**
     * constructor.
     */
    @Inject
    public UpdateFcmTokenAction(HoustonClient houstonClient,
                                UserRepository userRepository,
                                AuthRepository authRepository,
                                ExecutionTransformerFactory executionTransformerFactory,
                                NotificationActions notificationActions) {
        this.houstonClient = houstonClient;
        this.userRepository = userRepository;
        this.authRepository = authRepository;
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

        if (! authRepository.getSessionStatus().isPresent()) {
            // We don't have a session with Houston yet. This is our first FCM token, so there's
            // nothing to update. The token will be sent in the createSession API call. If it
            // changes later, while we are logged in, we'll update it as seen below.
            return Observable.just(null);
        }

        Timber.d("Updating FCM token");

        return houstonClient.updateFcmToken(token)
                .compose(executionTransformerFactory.getAsyncExecutor())
                .flatMap(ignore -> notificationActions.pullNotifications())
                // TODO: replace this with generic mechanism for all async actions to log errors
                .doOnError(error -> Timber.e(error, "Error while trying to refresh FCM token"));
    }
}
