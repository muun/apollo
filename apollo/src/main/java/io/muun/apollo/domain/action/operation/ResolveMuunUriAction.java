package io.muun.apollo.domain.action.operation;

import io.muun.apollo.data.db.contact.ContactDao;
import io.muun.apollo.data.preferences.FeeWindowRepository;
import io.muun.apollo.data.preferences.UserRepository;
import io.muun.apollo.domain.action.base.BaseAsyncAction1;
import io.muun.apollo.domain.model.Contact;
import io.muun.apollo.domain.model.FeeWindow;
import io.muun.apollo.domain.model.OperationUri;
import io.muun.apollo.domain.model.PaymentRequest;
import io.muun.apollo.domain.model.User;

import org.javamoney.moneta.Money;
import rx.Observable;

import java.math.BigDecimal;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.money.MonetaryAmount;

@Singleton
public class ResolveMuunUriAction extends BaseAsyncAction1<OperationUri, PaymentRequest> {

    private final UserRepository userRepository;
    private final ContactDao contactDao;
    private final FeeWindowRepository feeWindowRepository;

    /**
     * Resolves a Muun URI, fetching User and/or Contact as needed.
     */
    @Inject
    public ResolveMuunUriAction(UserRepository userRepository,
                                ContactDao contactDao,
                                FeeWindowRepository feeWindowRepository) {

        this.userRepository = userRepository;
        this.contactDao = contactDao;
        this.feeWindowRepository = feeWindowRepository;
    }

    @Override
    public Observable<PaymentRequest> action(OperationUri operationUri) {
        return Observable.fromCallable(() -> resolveMuunUri(operationUri));
    }

    private PaymentRequest resolveMuunUri(OperationUri uri) {
        final User user = userRepository.fetchOne();
        final FeeWindow feeWindow = feeWindowRepository.fetchOne();

        final String amountParam = uri.getParam(OperationUri.MUUN_AMOUNT)
                .orElse("0");

        final String currencyParam = uri.getParam(OperationUri.MUUN_CURRENCY)
                .orElse(user.primaryCurrency.getCurrencyCode());

        final String descriptionParam = uri.getParam(OperationUri.MUUN_DESCRIPTION)
                .orElse("");

        final MonetaryAmount amount = Money.of(
                new BigDecimal(amountParam),
                currencyParam.toUpperCase()
        );

        final double feeRate = feeWindow.getFastestFeeInSatoshisPerByte();

        switch (uri.getHost()) {
            case OperationUri.MUUN_HOST_CONTACT:
                final Contact contact = contactDao
                        .fetchByHid(uri.getContactHid())
                        .toBlocking()
                        .first();

                return PaymentRequest.toContact(contact, amount, descriptionParam, feeRate);

            case OperationUri.MUUN_HOST_EXTERNAL:
                final String externalAddress = uri.getExternalAddress();
                return PaymentRequest.toAddress(externalAddress, amount, descriptionParam, feeRate);

            default:
                throw new IllegalArgumentException("Invalid host: " + uri.getHost());
        }
    }
}
