package io.muun.apollo.domain.action.operation;

import io.muun.apollo.data.db.contact.ContactDao;
import io.muun.apollo.data.preferences.UserRepository;
import io.muun.apollo.domain.action.base.BaseAsyncAction1;
import io.muun.apollo.domain.model.Contact;
import io.muun.apollo.domain.model.ExchangeRateWindow;
import io.muun.apollo.domain.model.OperationUri;
import io.muun.apollo.domain.model.PaymentRequest;
import io.muun.apollo.domain.model.user.User;
import io.muun.apollo.domain.selector.ExchangeRateSelector;

import rx.Observable;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class ResolveMuunUriAction extends BaseAsyncAction1<OperationUri, PaymentRequest> {

    private final UserRepository userRepository;
    private final ContactDao contactDao;
    private final ExchangeRateSelector rateSelector;

    /**
     * Resolves a Muun URI, fetching User and/or Contact as needed.
     */
    @Inject
    public ResolveMuunUriAction(
            UserRepository userRepository,
            ContactDao contactDao,
            ExchangeRateSelector rateSelector
    ) {
        this.userRepository = userRepository;
        this.contactDao = contactDao;
        this.rateSelector = rateSelector;
    }

    @Override
    public Observable<PaymentRequest> action(OperationUri operationUri) {
        return Observable.fromCallable(() -> resolveMuunUri(operationUri));
    }

    private PaymentRequest resolveMuunUri(OperationUri uri) {
        final User user = userRepository.fetchOne();
        // TODO: this could cause unexpected behaviour since it may not be same rate window as
        // the one used in paymentContext. We've seen rates for some currencies suddenly being
        // dropped which may cause trouble if the primary currency is one of them.
        // We should try to receive an exchange rate window or have it fixed somehow.
        final ExchangeRateWindow rateWindow = rateSelector.getLatestWindow();

        final String amountParam = uri.getParam(OperationUri.MUUN_AMOUNT)
                .orElse("0");

        final String currencyParam = uri.getParam(OperationUri.MUUN_CURRENCY)
                .orElse(user.getPrimaryCurrency(rateWindow).getCurrencyCode());

        final String descriptionParam = uri.getParam(OperationUri.MUUN_DESCRIPTION)
                .orElse("");

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
