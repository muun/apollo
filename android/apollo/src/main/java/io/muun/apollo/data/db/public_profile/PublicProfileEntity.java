package io.muun.apollo.data.db.public_profile;

import io.muun.apollo.data.db.base.BaseEntity;
import io.muun.apollo.domain.model.PublicProfile;

import android.database.Cursor;
import androidx.annotation.NonNull;
import androidx.sqlite.db.SupportSQLiteDatabase;
import com.google.auto.value.AutoValue;
import com.squareup.sqldelight.prerelease.SqlDelightStatement;

@AutoValue
public abstract class PublicProfileEntity implements PublicProfileModel, BaseEntity {

    public static final Factory<PublicProfileEntity> FACTORY =
            new Factory<>(AutoValue_PublicProfileEntity::new);

    /**
     * Map from the model to the content values.
     */
    public static SqlDelightStatement fromModel(SupportSQLiteDatabase db, PublicProfile profile) {

        final PublicProfileModel.InsertPublicProfile insertStatement = new PublicProfileModel
                .InsertPublicProfile(db);

        insertStatement.bind(
                profile.getId() == null ? NULL_ID : profile.getId(),
                profile.getHid(),
                profile.firstName,
                profile.lastName,
                profile.profilePictureUrl
        );

        return insertStatement;
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
