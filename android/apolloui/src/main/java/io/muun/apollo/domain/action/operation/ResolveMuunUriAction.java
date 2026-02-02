package io.muun.apollo.domain.action.operation;

import io.muun.apollo.data.db.contact.ContactDao;
import io.muun.apollo.domain.action.base.BaseAsyncAction1;
import io.muun.apollo.domain.model.Contact;
import io.muun.apollo.domain.model.OperationUri;
import io.muun.apollo.domain.model.PaymentRequest;

import rx.Observable;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class ResolveMuunUriAction extends BaseAsyncAction1<OperationUri, PaymentRequest> {

    private final ContactDao contactDao;

    /**
     * Resolves a Muun URI, fetching User and/or Contact as needed.
     */
    @Inject
    public ResolveMuunUriAction(ContactDao contactDao) {
        this.contactDao = contactDao;
    }

    @Override
    public Observable<PaymentRequest> action(OperationUri operationUri) {
        return Observable.fromCallable(() -> resolveMuunUri(operationUri));
    }

    private PaymentRequest resolveMuunUri(OperationUri uri) {
        switch (uri.getHost()) {
            case OperationUri.MUUN_HOST_CONTACT:
                final Contact contact = contactDao
                        .fetchByHid(uri.getContactHid())
                        .toBlocking()
                        .first();

                return PaymentRequest.toContact(contact);

            case OperationUri.MUUN_HOST_EXTERNAL:
                final String externalAddress = uri.getExternalAddress();
                return PaymentRequest.toAddress(externalAddress);

            default:
                throw new IllegalArgumentException("Invalid host: " + uri.getHost());
        }
    }
}
