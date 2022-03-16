package io.muun.apollo.data.db.phone_contact;

import io.muun.apollo.data.db.base.BaseDao;
import io.muun.apollo.domain.model.PhoneContact;
import io.muun.common.model.Diff;

import com.squareup.sqlbrite3.BriteDatabase;
import rx.Completable;
import rx.Observable;

import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class PhoneContactDao extends BaseDao<PhoneContact> {

    /**
     * Constructor.
     */
    @Inject
    public PhoneContactDao() {
        super("phone_contacts");
    }

    @Override
    public Completable deleteAll() {
        return Completable.fromAction(delightDb.getPhoneContactQueries()::deleteAll);
    }

    @Override
    protected void storeUnsafe(final PhoneContact element) {

        delightDb.getPhoneContactQueries().insertPhoneContact(
                element.getId(),
                element.internalId,
                element.name,
                element.phoneNumber,
                element.phoneNumberHash,
                element.firstSeen,
                element.lastSeen,
                element.lastUpdated
        );
    }

    /**
     * Synchronize the PhoneContact table with a stream of system contacts.
     * @return Diff of phone number hashes added/deleted from the table.
     */
    public Observable<Diff<String>> syncWith(List<PhoneContact> contacts, long currentTs) {

        return Observable.fromCallable(() -> {
            final BriteDatabase.Transaction transaction = newTransaction();
            final Diff<String> diff = new Diff<>();

            try {
                // First, attempt to insert all newly found phone contacts. If a contact already
                // existed, it is ignored. The lastSeen field is set for all found contacts, new
                // and old.
                for (PhoneContact contact: contacts) {
                    insertOrIgnore(contact);
                    updateLastSeen(contact);
                }

                // Contacts where firstSeen matches currentTs were added for the first time now.
                for (PhoneContact newContact: selectFirstSeenAt(currentTs)) {
                    newContact.generateHash();
                    updatePhoneHash(newContact);
                    diff.added.add(newContact.phoneNumberHash);
                }

                // Contacts where lastSeen doesn't match currentTs were removed since the last scan.
                for (PhoneContact deletedContact: selectLastSeenNotAt(currentTs)) {
                    diff.removed.add(deletedContact.phoneNumberHash);
                }

                deleteLastSeenNotAt(currentTs);

                transaction.markSuccessful();
                return diff;

            } finally {
                transaction.end();
            }
        });
    }

    private List<PhoneContact> selectFirstSeenAt(long currentTs) {
        return delightDb.getPhoneContactQueries()
                .selectFirstSeenAt(currentTs, PhoneContact::new)
                .executeAsList();
    }

    private List<PhoneContact> selectLastSeenNotAt(long currentTs) {
        return delightDb.getPhoneContactQueries()
                .selectFirstSeenAt(currentTs, PhoneContact::new)
                .executeAsList();
    }

    private void insertOrIgnore(PhoneContact contact) {
        delightDb.getPhoneContactQueries().insertOrIgnore(
                contact.internalId,
                contact.name,
                contact.phoneNumber,
                contact.firstSeen,
                contact.lastSeen,
                contact.lastUpdated
        );
    }

    private void updateLastSeen(PhoneContact contact) {
        delightDb.getPhoneContactQueries().updateLastSeen(
                contact.lastSeen, contact.internalId, contact.phoneNumber
        );
    }

    private void updatePhoneHash(PhoneContact contact) {
        delightDb.getPhoneContactQueries().updatePhoneHash(
                contact.phoneNumberHash, contact.getId()
        );
    }

    private void deleteLastSeenNotAt(long currentTs) {
        delightDb.getPhoneContactQueries().deleteLastSeenNotAt(currentTs);
    }

}
