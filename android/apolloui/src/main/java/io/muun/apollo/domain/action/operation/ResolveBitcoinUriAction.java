package io.muun.apollo.domain.action.operation;

import io.muun.apollo.domain.action.base.BaseAsyncAction1;
import io.muun.apollo.domain.errors.newop.InvalidPaymentRequestError;
import io.muun.apollo.domain.libwallet.LibwalletBridge;
import io.muun.apollo.domain.model.BitcoinUriContent;
import io.muun.apollo.domain.model.OperationUri;
import io.muun.apollo.domain.model.PaymentRequest;

import rx.Observable;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class ResolveBitcoinUriAction extends BaseAsyncAction1<OperationUri, PaymentRequest> {


    /**
     * Resolves a Bitcoin URI, using BIP-72 or BIP-21 as appropriate.
     */
    @Inject
    public ResolveBitcoinUriAction() {

    }

    @Override
    public Observable<PaymentRequest> action(OperationUri operationUri) {
        return Observable.fromCallable(() -> {
            try {
                return resolveBitcoinUri(operationUri);

            } catch (InvalidPaymentRequestError e) {
                throw new InvalidPaymentRequestError(operationUri.toString(), e);
            }
        });
    }

    private PaymentRequest resolveBitcoinUri(OperationUri uri) {
        final BitcoinUriContent uriContent = LibwalletBridge.getBitcoinUriContent(uri);

        return PaymentRequest.toAddress(uriContent.address);
    }

}
