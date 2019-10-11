package io.muun.apollo.data.db.phone_contact;

import io.muun.apollo.data.db.base.BaseEntity;
import io.muun.apollo.domain.model.PhoneContact;

import android.database.Cursor;
import androidx.sqlite.db.SupportSQLiteDatabase;
import com.google.auto.value.AutoValue;
import com.squareup.sqldelight.prerelease.SqlDelightStatement;

@AutoValue
public abstract class PhoneContactEntity implements PhoneContactModel, BaseEntity {

    public static final Factory<PhoneContactEntity> FACTORY =
            new Factory<>(AutoValue_PhoneContactEntity::new);

    /**
     * Map from the model to the content values.
     */
    public static SqlDelightStatement fromModel(SupportSQLiteDatabase db,
                                                PhoneContact phoneContact) {

        final PhoneContactModel.InsertPhoneContact insertStatement = new PhoneContactModel
                .InsertPhoneContact(db);

        insertStatement.bind(
                phoneContact.getId() == null ? BaseEntity.NULL_ID : phoneContact.getId(),
                phoneContact.internalId,
                phoneContact.name,
                phoneContact.phoneNumber,
                phoneContact.phoneNumberHash,
                phoneContact.firstSeen,
                phoneContact.lastSeen,
                phoneContact.lastUpdated
        );

        return insertStatement;
    }

    /**
     * Map from the database cursor to the model.
     */
    public static PhoneContact toModel(Cursor cursor) {
        final PhoneContactEntity entity = FACTORY.selectAllMapper().map(cursor);

        return new PhoneContact(
                entity.id(),
                entity.internal_id(),
                entity.name(),
                entity.phone_number(),
                entity.phone_number_hash(),
                entity.first_seen(),
                entity.last_seen(),
                entity.last_updated()
        );
    }
}
