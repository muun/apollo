package io.muun.apollo.domain.action.fcm;

import io.muun.apollo.data.net.HoustonClient;
import io.muun.apollo.data.os.execution.ExecutionTransformerFactory;
import io.muun.apollo.data.preferences.AuthRepository;
import io.muun.apollo.data.preferences.FcmTokenRepository;
import io.muun.apollo.domain.action.NotificationActions;
import io.muun.apollo.domain.action.base.BaseAsyncAction1;
import io.muun.common.model.SessionStatus;

import rx.Observable;
import timber.log.Timber;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class UpdateFcmTokenAction extends BaseAsyncAction1<String, Void> {

    private final HoustonClient houstonClient;
    private final FcmTokenRepository fcmTokenRepository;
    private final AuthRepository authRepository;
    private final ExecutionTransformerFactory executionTransformerFactory;
    private final NotificationActions notificationActions;

    /**
     * constructor.
     */
    @Inject
    public UpdateFcmTokenAction(HoustonClient houstonClient,
                                FcmTokenRepository fcmTokenRepository,
                                AuthRepository authRepository,
                                ExecutionTransformerFactory executionTransformerFactory,
                                NotificationActions notificationActions) {
        this.houstonClient = houstonClient;
        this.fcmTokenRepository = fcmTokenRepository;
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

        fcmTokenRepository.storeFcmToken(token);

        final boolean hasValidSession = authRepository.getSessionStatus().isPresent()
                && authRepository.getSessionStatus().get() != SessionStatus.EXPIRED;
        final boolean hasJwt = authRepository.getServerJwt().isPresent();

        // If anyone of these is true, we can't perform an http request to Houston (NOT_AUTHORIZED)
        if (!hasValidSession || !hasJwt) {

            // As IDE will tell you, !hasJwt will always be true at this point, but we're leaving it
            // - for readability
            // - to explicitly state the policy, so that (possible) future changes don't miss this
            final boolean hasValidSessionButNoJwt = hasValidSession && !hasJwt;
            final boolean hasJwtButInvalidSession = !hasValidSession && hasJwt;

            // Integrity check. May sound silly but its our canary for when things go wrong with
            // a logout (our logout logic has become a bit cumbersome).
            if (hasValidSessionButNoJwt || hasJwtButInvalidSession) {
                Timber.e("Integrity error! Probably something went wrong with a logout");
            }

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
