package io.muun.apollo.data.db.public_profile;

import io.muun.apollo.data.db.base.HoustonIdDao;
import io.muun.apollo.domain.model.PublicProfile;

import androidx.annotation.NonNull;
import rx.Completable;
import rx.Observable;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class PublicProfileDao extends HoustonIdDao<PublicProfile> {

    /**
     * Constructor.
     */
    @Inject
    public PublicProfileDao() {
        super("public_profiles");
    }

    @Override
    public Completable deleteAll() {
        return Completable.fromAction(delightDb.getPublicProfileQueries()::deleteAll);
    }

    @Override
    protected void storeUnsafe(@NonNull final PublicProfile element) {
        delightDb.getPublicProfileQueries().insertPublicProfile(
                element.getId(),
                element.getHid(),
                element.firstName,
                element.lastName,
                element.profilePictureUrl
        );
    }

    /**
     * Update all the mutable data of a public profile.
     */
    public void update(PublicProfile publicProfile) {
        delightDb.getPublicProfileQueries().updateProfile(
                publicProfile.firstName,
                publicProfile.lastName,
                publicProfile.profilePictureUrl,
                publicProfile.getHid()
        );
    }

    /**
     * Fetches a single public profile by its Houston id.
     */
    public Observable<PublicProfile> fetchByHid(long contactHid) {
        return fetchOneOrFail(delightDb.getPublicProfileQueries().selectByHid(
                contactHid,
                PublicProfile::new
        ))
                .doOnError(error -> enhanceError(error, String.valueOf(contactHid)));
    }

    /**
     * BLOCK while fetching a single public profile by its Houston id.
     */
    public PublicProfile fetchOneByHid(long contactHid) {
        return fetchByHid(contactHid).toBlocking().first();
    }
}
