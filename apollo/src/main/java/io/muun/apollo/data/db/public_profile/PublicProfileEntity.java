package io.muun.apollo.data.db.public_profile;

import io.muun.apollo.data.db.base.BaseEntity;
import io.muun.apollo.domain.model.PublicProfile;

import android.content.ContentValues;
import android.database.Cursor;
import android.support.annotation.NonNull;
import com.google.auto.value.AutoValue;

@AutoValue
public abstract class PublicProfileEntity implements PublicProfileModel, BaseEntity {

    public static final Factory<PublicProfileEntity> FACTORY =
            new Factory<>(AutoValue_PublicProfileEntity::new);

    /**
     * Map from the model to the content values.
     */
    public static ContentValues fromModel(PublicProfile profile) {

        return FACTORY.marshal()
                .id(profile.id == null ? NULL_ID : profile.id)
                .hid(profile.hid)
                .first_name(profile.firstName)
                .last_name(profile.lastName)
                .profile_picture_url(profile.profilePictureUrl)
                .asContentValues();
    }

    /**
     * Map from the database cursor to the model.
     */
    public static PublicProfile toModel(Cursor cursor) {

        final PublicProfileEntity entity = FACTORY.selectAllMapper().map(cursor);

        return getPublicProfile(entity);
    }

    /**
     * Builds a PublicProfile domain layer model from a data layer PublicProfileEntity.
     */
    @NonNull
    public static PublicProfile getPublicProfile(PublicProfileEntity entity) {
        return new PublicProfile(
                entity.id(),
                entity.hid(),
                entity.first_name(),
                entity.last_name(),
                entity.profile_picture_url()
        );
    }
}
