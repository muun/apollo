package io.muun.apollo.domain.action.contacts;

import io.muun.apollo.data.db.contact.ContactDao;
import io.muun.apollo.data.db.public_profile.PublicProfileDao;
import io.muun.apollo.data.net.HoustonClient;
import io.muun.apollo.domain.action.base.BaseAsyncAction1;
import io.muun.apollo.domain.model.Contact;

import rx.Observable;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class FetchSingleContactAction extends BaseAsyncAction1<Contact, Contact> {

    private final ContactDao contactDao;
    private final PublicProfileDao publicProfileDao;

    private final HoustonClient houstonClient;

    /**
     * Fetch and replace data for a single contact from Houston.
     */
    @Inject
    public FetchSingleContactAction(ContactDao contactDao,
                                    PublicProfileDao publicProfileDao,
                                    HoustonClient houstonClient) {

        this.contactDao = contactDao;
        this.publicProfileDao = publicProfileDao;
        this.houstonClient = houstonClient;
    }

    @Override
    public Observable<Contact> action(Contact contact) {
        return houstonClient.fetchContact(contact.publicProfile.hid)
                .doOnNext(remoteContact -> {
                    publicProfileDao.store(remoteContact.publicProfile).toCompletable().await();
                    contactDao.store(remoteContact).toCompletable().await();
                });
    }
}
