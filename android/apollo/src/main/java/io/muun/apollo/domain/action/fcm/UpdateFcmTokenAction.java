package io.muun.apollo.domain.action.fcm;

import io.muun.apollo.data.net.HoustonClient;
import io.muun.apollo.data.preferences.AuthRepository;
import io.muun.apollo.data.preferences.FirebaseInstallationIdRepository;
import io.muun.apollo.domain.action.NotificationActions;
import io.muun.apollo.domain.action.base.BaseAsyncAction1;
import io.muun.apollo.domain.errors.LocalStorageIntegrityError;
import io.muun.common.model.SessionStatus;

import rx.Observable;
import timber.log.Timber;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class UpdateFcmTokenAction extends BaseAsyncAction1<String, Void> {

    private final HoustonClient houstonClient;
    private final FirebaseInstallationIdRepository firebaseInstallationIdRepository;
    private final AuthRepository authRepository;
    private final NotificationActions notificationActions;

    /**
     * constructor.
     */
    @Inject
    public UpdateFcmTokenAction(HoustonClient houstonClient,
                                FirebaseInstallationIdRepository firebaseInstallationIdRepository,
                                AuthRepository authRepository,
                                NotificationActions notificationActions) {
        this.houstonClient = houstonClient;
        this.firebaseInstallationIdRepository = firebaseInstallationIdRepository;
        this.authRepository = authRepository;
        this.notificationActions = notificationActions;
    }

    @Override
    public Observable<Void> action(String newFcmToken) {

        firebaseInstallationIdRepository.storeFcmToken(newFcmToken);

        final boolean hasJwt = authRepository.getServerJwt().isPresent();
        final boolean hasValidSession = authRepository.getSessionStatus()
                .filter(status -> status != SessionStatus.EXPIRED)
                .isPresent();

        // If anyone of these is true, we can't perform an http request to Houston (NOT_AUTHORIZED)
        if (!hasValidSession || !hasJwt) {

            // As IDE might tell you, !hasJwt will always be true at this point. We're leaving it:
            // - for readability
            // - to explicitly state the policy, so that (possible) future changes don't miss this
            @SuppressWarnings("ConstantConditions") // Don't warn about value guaranteed to be const
            final boolean hasValidSessionButNoJwt = hasValidSession && !hasJwt;
            final boolean hasJwtButInvalidSession = !hasValidSession && hasJwt;

            // Integrity check. May sound silly but its our canary for when things go wrong with
            // a logout (our logout logic has become a bit cumbersome) or a local storage wipe.
            if (hasValidSessionButNoJwt || hasJwtButInvalidSession) {
                Timber.e(new LocalStorageIntegrityError(
                        hasValidSessionButNoJwt,
                        hasJwtButInvalidSession,
                        newFcmToken
                ));
            }

            // We don't have a session with Houston yet. This is our first FCM token, so there's
            // nothing to update. The token will be sent in the createSession API call. If it
            // changes later, while we are logged in, we'll update it as seen below.
            return Observable.just(null);
        }

        Timber.d("Updating FCM token");

        return houstonClient.updateFcmToken(newFcmToken)
                .flatMap(ignore -> notificationActions.pullNotifications());
    }
}
