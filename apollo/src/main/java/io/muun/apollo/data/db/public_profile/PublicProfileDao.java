package io.muun.apollo.data.db.public_profile;

import io.muun.apollo.data.db.base.HoustonDao;
import io.muun.apollo.domain.model.PublicProfile;

import rx.Observable;

import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class PublicProfileDao extends HoustonDao<PublicProfile> {

    /**
     * Constructor.
     */
    @Inject
    public PublicProfileDao() {

        super(
                PublicProfileEntity.CREATE_TABLE,
                PublicProfileEntity::fromModel,
                PublicProfileEntity::toModel,
                PublicProfileEntity.TABLE_NAME
        );
    }

    /**
     * Update all the mutable data of a public profile.
     */
    public void update(PublicProfile publicProfile) {

        final PublicProfileModel.UpdateProfile statement =
                new PublicProfileEntity.UpdateProfile(db);

        statement.bind(
                publicProfile.firstName,
                publicProfile.lastName,
                publicProfile.profilePictureUrl,
                publicProfile.hid
        );

        executeStatement(statement);
    }

    /**
     * Fetches a single public profile by its Houston id.
     */
    public Observable<PublicProfile> fetchByHid(long contactHid) {

        return fetchOneOrFail(PublicProfileEntity.FACTORY.selectByHid(contactHid));
    }

    /**
     * BLOCK while fetching a single public profile by its Houston id.
     */
    public PublicProfile fetchOneByHid(long contactHid) {
        return fetchByHid(contactHid).toBlocking().first();
    }
}
