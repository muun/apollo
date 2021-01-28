package io.muun.apollo.domain.action.user;

import io.muun.apollo.data.net.HoustonClient;
import io.muun.apollo.data.preferences.UserRepository;
import io.muun.apollo.domain.action.base.BaseAsyncAction0;
import io.muun.apollo.domain.model.UserProfile;

import android.net.Uri;
import rx.Observable;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class UpdateProfilePictureAction extends BaseAsyncAction0<UserProfile> {

    private final UserRepository userRepository;
    private final HoustonClient houstonClient;

    /**
     * Uploads the pending profile picture if present.
     */
    @Inject
    public UpdateProfilePictureAction(UserRepository userRepository, HoustonClient houstonClient) {
        this.userRepository = userRepository;
        this.houstonClient = houstonClient;
    }

    @Override
    public Observable<UserProfile> action() {

        final Uri pendingProfilePictureUri = userRepository.getPendingProfilePictureUri();

        if (pendingProfilePictureUri == null) {
            return Observable.just(null);
        }

        return houstonClient.uploadProfilePicture(pendingProfilePictureUri)
                .doOnNext(userRepository::storeProfile)
                .doOnNext(ignore -> userRepository.setPendingProfilePictureUri(null));
    }
}
