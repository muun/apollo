package io.muun.apollo.data.db.phone_contact;

import io.muun.apollo.data.db.base.BaseDao;
import io.muun.apollo.domain.model.PhoneContact;
import io.muun.common.model.Diff;

import com.squareup.sqlbrite.BriteDatabase;
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

        super(
                PhoneContactEntity.CREATE_TABLE,
                PhoneContactEntity::fromModel,
                PhoneContactEntity::toModel,
                PhoneContactEntity.TABLE_NAME
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
        return fetchListOnce(PhoneContactEntity.FACTORY.selectFirstSeenAt(currentTs));
    }

    private List<PhoneContact> selectLastSeenNotAt(long currentTs) {
        return fetchListOnce(PhoneContactEntity.FACTORY.selectLastSeenNotAt(currentTs));
    }

    private void insertOrIgnore(PhoneContact contact) {
        final PhoneContactEntity.InsertOrIgnore q = new PhoneContactEntity.InsertOrIgnore(db);

        q.bind(
                contact.internalId,
                contact.name,
                contact.phoneNumber,
                contact.firstSeen,
                contact.lastSeen,
                contact.lastUpdated
        );

        executeStatement(q);
    }

    private void updateLastSeen(PhoneContact contact) {
        final PhoneContactModel.UpdateLastSeen q = new PhoneContactModel.UpdateLastSeen(db);

        q.bind(contact.lastSeen, contact.internalId, contact.phoneNumber);
        executeStatement(q);
    }

    private void updatePhoneHash(PhoneContact contact) {
        final PhoneContactModel.UpdatePhoneHash q = new PhoneContactModel.UpdatePhoneHash(db);

        q.bind(contact.phoneNumberHash, contact.getId());
        executeStatement(q);
    }

    private void deleteLastSeenNotAt(long currentTs) {
        final PhoneContactModel.DeleteLastSeenNotAt q =
                new PhoneContactModel.DeleteLastSeenNotAt(db);

        q.bind(currentTs);
        executeStatement(q);
    }
}
