package io.muun.apollo.domain.action.operation;

import io.muun.apollo.data.db.contact.ContactDao;
import io.muun.apollo.data.db.hwallet.HardwareWalletDao;
import io.muun.apollo.data.preferences.UserRepository;
import io.muun.apollo.domain.action.base.BaseAsyncAction1;
import io.muun.apollo.domain.model.Contact;
import io.muun.apollo.domain.model.HardwareWallet;
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
    private final HardwareWalletDao hardwareWalletDao;

    /**
     * Resolves a Muun URI, fetching User, Contact and/or HardwareWallet as needed.
     */
    @Inject
    public ResolveMuunUriAction(UserRepository userRepository,
                                ContactDao contactDao,
                                HardwareWalletDao hardwareWalletDao) {

        this.userRepository = userRepository;
        this.contactDao = contactDao;
        this.hardwareWalletDao = hardwareWalletDao;
    }

    @Override
    public Observable<PaymentRequest> action(OperationUri operationUri) {
        return Observable.fromCallable(() -> resolveMuunUri(operationUri));
    }

    private PaymentRequest resolveMuunUri(OperationUri uri) {
        final User user = userRepository.fetchOne();

        final String amountParam = uri.getParam(OperationUri.MUUN_AMOUNT)
                .orElse("0");

        final String currencyParam = uri.getParam(OperationUri.MUUN_CURRENCY)
                .orElse(user.primaryCurrency.getCurrencyCode());

        final String descriptionParam = uri.getParam(OperationUri.MUUN_DESCRIPTION)
                .orElse("");

        final MonetaryAmount amount = Money.of(new BigDecimal(amountParam), currencyParam);

        switch (uri.getHost()) {
            case OperationUri.MUUN_HOST_CONTACT:
                final Contact contact = contactDao
                        .fetchByHid(Long.parseLong(uri.getPath()))
                        .toBlocking()
                        .first();

                return PaymentRequest.toContact(contact, amount, descriptionParam);

            case OperationUri.MUUN_HOST_EXTERNAL:
                return PaymentRequest.toAddress(uri.getPath(), amount, descriptionParam);

            case OperationUri.MUUN_HOST_DEPOSIT:
                final HardwareWallet receiver = hardwareWalletDao
                        .fetchByHid(Long.parseLong(uri.getPath()))
                        .toBlocking()
                        .first();

                return PaymentRequest.toHardwareWallet(receiver, amount, descriptionParam);

            case OperationUri.MUUN_HOST_WITHDRAW:
                final HardwareWallet sender = hardwareWalletDao
                        .fetchByHid(Long.parseLong(uri.getPath()))
                        .toBlocking()
                        .first();

                return PaymentRequest.fromHardwareWallet(sender, amount, descriptionParam);

            default:
                throw new IllegalArgumentException("Invalid host: " + uri.getHost());
        }
    }
}
