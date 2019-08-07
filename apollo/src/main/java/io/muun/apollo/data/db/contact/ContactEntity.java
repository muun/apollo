package io.muun.apollo.data.db.contact;

import io.muun.apollo.data.db.base.BaseEntity;
import io.muun.apollo.data.db.public_profile.PublicProfileEntity;
import io.muun.apollo.domain.model.Contact;
import io.muun.apollo.domain.model.PublicProfile;
import io.muun.common.crypto.hd.PublicKey;

import android.content.ContentValues;
import android.database.Cursor;
import com.google.auto.value.AutoValue;
import rx.functions.Func1;

@AutoValue
public abstract class ContactEntity implements ContactModel, BaseEntity {

    public static final Factory<ContactEntity> FACTORY =
            new Factory<>(AutoValue_ContactEntity::new);

    @AutoValue
    public abstract static class CompleteContact implements
            ContactModel.SelectAllModel<ContactEntity, PublicProfileEntity> {
    }

    /**
     * Map from the model to the content values.
     */
    public static Func1<Contact, ContentValues> fromModel() {

        return contact -> FACTORY.marshal()
                .id(contact.getId() == null ? BaseEntity.NULL_ID : contact.getId())
                .hid(contact.getHid())
                .max_address_version(contact.maxAddressVersion)
                .public_key(contact.publicKey.serializeBase58())
                .public_key_path(contact.publicKey.getAbsoluteDerivationPath())
                .cosigning_public_key(contact.cosigningPublicKey != null
                        ? contact.cosigningPublicKey.serializeBase58()
                        : null
                )
                .cosigning_public_key_path(contact.cosigningPublicKey != null
                        ? contact.cosigningPublicKey.getAbsoluteDerivationPath()
                        : null)
                .last_derivation_index(contact.lastDerivationIndex)
                .asContentValues();
    }

    /**
     * Map from the database cursor to the model.
     */
    public static Func1<Cursor, Contact> toModel() {

        return cursor -> {

            final CompleteContact entity = FACTORY.selectAllMapper(
                    AutoValue_ContactEntity_CompleteContact::new,
                    PublicProfileEntity.FACTORY
            ).map(cursor);

            final PublicKey cosigningPublicKey;
            if (entity.contacts().cosigning_public_key() != null) {
                cosigningPublicKey = PublicKey.deserializeFromBase58(
                        entity.contacts().cosigning_public_key_path(),
                        entity.contacts().cosigning_public_key()
                );
            } else {
                cosigningPublicKey = null;
            }

            return new Contact(
                    entity.contacts().id(),
                    entity.contacts().hid(),
                    new PublicProfile(
                            entity.public_profiles().id(),
                            entity.public_profiles().hid(),
                            entity.public_profiles().first_name(),
                            entity.public_profiles().last_name(),
                            entity.public_profiles().profile_picture_url()
                    ),
                    (int) entity.contacts().max_address_version(),
                    PublicKey.deserializeFromBase58(
                            entity.contacts().public_key_path(),
                            entity.contacts().public_key()
                    ),
                    cosigningPublicKey,
                    entity.contacts().last_derivation_index()
            );
        };
    }
}
