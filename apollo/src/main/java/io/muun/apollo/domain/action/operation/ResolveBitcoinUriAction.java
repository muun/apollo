package io.muun.apollo.domain.action.operation;

import io.muun.apollo.data.preferences.FeeWindowRepository;
import io.muun.apollo.domain.action.base.BaseAsyncAction1;
import io.muun.apollo.domain.errors.InvalidPaymentRequestError;
import io.muun.apollo.domain.libwallet.LibwalletBridge;
import io.muun.apollo.domain.model.BitcoinUriContent;
import io.muun.apollo.domain.model.FeeWindow;
import io.muun.apollo.domain.model.OperationUri;
import io.muun.apollo.domain.model.PaymentRequest;
import io.muun.apollo.domain.utils.StringUtils;
import io.muun.common.utils.BitcoinUtils;

import org.bitcoinj.core.NetworkParameters;
import rx.Observable;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.money.MonetaryAmount;

@Singleton
public class ResolveBitcoinUriAction extends BaseAsyncAction1<OperationUri, PaymentRequest> {

    private final NetworkParameters networkParameters;
    private final FeeWindowRepository feeWindowRepository;

    /**
     * Resolves a Bitcoin URI, using BIP-72 or BIP-21 as appropriate.
     */
    @Inject
    public ResolveBitcoinUriAction(NetworkParameters networkParameters,
                                   FeeWindowRepository feeWindowRepository) {

        this.networkParameters = networkParameters;
        this.feeWindowRepository = feeWindowRepository;
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
        final FeeWindow feeWindow = feeWindowRepository.fetchOne();

        final BitcoinUriContent uriContent = LibwalletBridge.getBitcoinUriContent(uri);

        final MonetaryAmount amount = (uriContent.amountInStatoshis != null)
                ? BitcoinUtils.satoshisToBitcoins(uriContent.amountInStatoshis)
                : null;

        final String description = StringUtils.joinText(": ", new String[]{
                uriContent.merchant,
                uriContent.memo
        });

        final double feeRate = feeWindow.getFastestFeeInSatoshisPerByte();

        return PaymentRequest.toAddress(uriContent.address, amount, description, feeRate);
    }

}
