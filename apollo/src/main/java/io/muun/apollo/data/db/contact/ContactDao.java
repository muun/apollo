package io.muun.apollo.data.db.contact;

import io.muun.apollo.data.db.base.HoustonIdDao;
import io.muun.apollo.domain.model.Contact;

import rx.Observable;

import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class ContactDao extends HoustonIdDao<Contact> {

    /**
     * Constructor.
     */
    @Inject
    public ContactDao() {
        super(
                ContactEntity.CREATE_TABLE,
                ContactEntity::fromModel,
                ContactEntity::toModel,
                ContactEntity.TABLE_NAME
        );
    }

    /**
     * Update the last derivation index to the maximum between the current index and the one given.
     */
    public void updateLastDerivationIndex(long contactHid, long lastDerivationIndex) {

        final ContactModel.UpdateLastDerivationIndex statement =
                new ContactEntity.UpdateLastDerivationIndex(db);

        statement.bind(lastDerivationIndex, contactHid);

        executeUpdate(statement);
    }

    /**
     * Fetches all contacts from the db.
     */
    public Observable<List<Contact>> fetchAll() {
        return fetchList(ContactEntity.FACTORY.selectAll());
    }

    /**
     * Fetches a single contact by its Houston id.
     */
    public Observable<Contact> fetchByHid(long contactHid) {
        return fetchOneOrFail(ContactEntity.FACTORY.selectByHid(contactHid));
    }
}
