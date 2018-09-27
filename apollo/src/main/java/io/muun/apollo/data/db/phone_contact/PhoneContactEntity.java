package io.muun.apollo.data.db.phone_contact;

import io.muun.apollo.data.db.base.BaseEntity;
import io.muun.apollo.domain.model.PhoneContact;

import android.content.ContentValues;
import android.database.Cursor;
import com.google.auto.value.AutoValue;

@AutoValue
public abstract class PhoneContactEntity implements PhoneContactModel, BaseEntity {

    public static final Factory<PhoneContactEntity> FACTORY =
            new Factory<>(AutoValue_PhoneContactEntity::new);

    /**
     * Map from the model to the content values.
     */
    public static ContentValues fromModel(PhoneContact phoneContact) {

        return FACTORY.marshal()
                .id(phoneContact.id == null ? BaseEntity.NULL_ID : phoneContact.id)
                .internal_id(phoneContact.internalId)
                .name(phoneContact.name)
                .phone_number(phoneContact.phoneNumber)
                .phone_number_hash(phoneContact.phoneNumberHash)
                .first_seen(phoneContact.firstSeen)
                .last_seen(phoneContact.lastSeen)
                .last_updated(phoneContact.lastUpdated)
                .asContentValues();
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
