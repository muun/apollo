package io.muun.apollo.data.db.contact;

import io.muun.apollo.data.db.base.HoustonIdDao;
import io.muun.apollo.domain.model.Contact;
import io.muun.apollo.domain.model.PublicProfile;
import io.muun.common.crypto.hd.PublicKey;

import rx.Completable;
import rx.Observable;

import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class ContactDao extends HoustonIdDao<Contact> {

    /**
     * Constructor.
     */
    @Inject
    public ContactDao() {
        super("contacts");
    }

    @Override
    public Completable deleteAll() {
        return Completable.fromAction(() -> delightDb.getContactQueries().deleteAll());
    }

    @Override
    protected void storeUnsafe(final Contact element) {
        final PublicKey userPublicKey = element.getPublicKeyPair().getUserPublicKey();
        final PublicKey muunPublicKey = element.getPublicKeyPair().getMuunPublicKey();

        delightDb.getContactQueries().insertContact(
                element.getId(),
                element.getHid(),
                userPublicKey.serializeBase58(),
                userPublicKey.getAbsoluteDerivationPath(),
                element.lastDerivationIndex,
                element.maxAddressVersion,
                muunPublicKey != null ? muunPublicKey.serializeBase58() : null,
                muunPublicKey != null ? muunPublicKey.getAbsoluteDerivationPath() : null
        );
    }

    /**
     * Update the last derivation index to the maximum between the current index and the one given.
     */
    public void updateLastDerivationIndex(long contactHid, long lastDerivationIndex) {
        delightDb.getContactQueries().updateLastDerivationIndex(lastDerivationIndex, contactHid);
    }

    /**
     * Fetches all contacts from the db.
     */
    public Observable<List<Contact>> fetchAll() {
        return fetchList(delightDb.getContactQueries().selectAll(this::fromAllFields));
    }

    /**
     * Fetches a single contact by its Houston id.
     */
    public Observable<Contact> fetchByHid(long contactHid) {
        return fetchOneOrFail(
                delightDb.getContactQueries().selectByHid(contactHid, this::fromAllFields)
        ).doOnError(error -> enhanceError(error, String.valueOf(contactHid)));
    }

    private Contact fromAllFields(
            long id,
            long hid,
            @Nonnull String serializedPublicKey,
            @Nonnull String publicKeyPath,
            long lastDerivationIndex,
            long maxAddressVersion,
            @Nullable String serializedCosigningPublicKey,
            @Nullable String cosigningPublicKeyPath,
            long publicProfileId,
            long publicProfileHid,
            @Nonnull String firstName,
            @Nonnull String lastName,
            @Nullable String profilePictureUrl
    ) {
        final PublicKey cosigningPublicKey;
        if (serializedCosigningPublicKey != null) {
            cosigningPublicKey = PublicKey.deserializeFromBase58(
                    cosigningPublicKeyPath,
                    serializedCosigningPublicKey
            );
        } else {
            cosigningPublicKey = null;
        }

        return new Contact(
                id,
                hid,
                new PublicProfile(
                        publicProfileId,
                        publicProfileHid,
                        firstName,
                        lastName,
                        profilePictureUrl
                ),
                Math.toIntExact(maxAddressVersion),
                PublicKey.deserializeFromBase58(publicKeyPath, publicKeyPath),
                cosigningPublicKey,
                lastDerivationIndex
        );
    }
}
