package io.muun.apollo.domain.action.operation;

import io.muun.apollo.data.logging.Logger;
import io.muun.apollo.data.net.base.NetworkException;
import io.muun.apollo.domain.action.base.BaseAsyncAction1;
import io.muun.apollo.domain.errors.InvalidPaymentRequestError;
import io.muun.apollo.domain.model.BitcoinUriContent;
import io.muun.apollo.domain.model.OperationUri;
import io.muun.apollo.domain.model.PaymentRequest;
import io.muun.apollo.domain.utils.StringUtils;
import io.muun.apollo.external.Globals;
import io.muun.common.bitcoinj.BitcoinUri;
import io.muun.common.bitcoinj.ValidationHelpers;
import io.muun.common.utils.BitcoinUtils;

import android.support.annotation.VisibleForTesting;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.protocols.payments.PaymentProtocol;
import org.bitcoinj.protocols.payments.PaymentProtocolException;
import org.bitcoinj.protocols.payments.PaymentSession;
import org.bitcoinj.script.Script;
import org.bitcoinj.uri.BitcoinURIParseException;
import rx.Observable;

import java.util.concurrent.ExecutionException;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.money.MonetaryAmount;

@Singleton
public class ResolveBitcoinUriAction extends BaseAsyncAction1<OperationUri, PaymentRequest> {

    private final NetworkParameters networkParameters;

    /**
     * Resolves a Bitcoin URI, using BIP-72 or BIP-21 as appropriate.
     */
    @Inject
    public ResolveBitcoinUriAction(NetworkParameters networkParameters) {
        this.networkParameters = networkParameters;
    }

    @Override
    public Observable<PaymentRequest> action(OperationUri operationUri) {
        return Observable.fromCallable(() -> resolveBitcoinUri(operationUri));
    }

    private PaymentRequest resolveBitcoinUri(OperationUri uri) {
        final BitcoinUriContent uriContent = getBitcoinUriContent(uri);

        final MonetaryAmount amount = (uriContent.amountInStatoshis != null)
                ? BitcoinUtils.satoshisToBitcoins(uriContent.amountInStatoshis)
                : null;

        final String description = StringUtils.joinText(": ", new String[]{
                uriContent.merchant,
                uriContent.memo
        });

        return PaymentRequest.toAddress(uriContent.address, amount, description);
    }

    @VisibleForTesting
    BitcoinUriContent getBitcoinUriContent(OperationUri uri) {
        if (uri.getAsyncUrl().isPresent()) {
            return getBitcoinUriContent72fallback21(uri.toString());
        } else {
            return getBitcoinUriContent21(uri.toString());
        }
    }

    @VisibleForTesting
    BitcoinUriContent getBitcoinUriContent21(String uriString) {
        final BitcoinUri bitcoinUri = parseBitcoinUri(uriString);

        return getPaymentRequestBip21(bitcoinUri);
    }

    @VisibleForTesting
    BitcoinUriContent getBitcoinUriContent72fallback21(String uriString) {
        final BitcoinUri bitcoinUri = parseBitcoinUri(uriString);

        try {
            return getPaymentRequestBip72(bitcoinUri);

        } catch (Throwable e) {
            Logger.error(e, "Falling back to BIP-21");
            return getPaymentRequestBip21(bitcoinUri);
        }
    }

    private BitcoinUri parseBitcoinUri(String text) {
        try {
            return new BitcoinUri(networkParameters, text);

        } catch (BitcoinURIParseException e) {
            throw new InvalidPaymentRequestError(e);
        }
    }

    private BitcoinUriContent getPaymentRequestBip21(BitcoinUri bitcoinUri) {
        return toBitcoinUriContent(
                bitcoinUri.getAddress(),
                bitcoinUri.getAmount(),
                bitcoinUri.getMessage(),
                bitcoinUri.getLabel()
        );
    }

    private BitcoinUriContent getPaymentRequestBip72(BitcoinUri bitcoinUri) {
        final PaymentSession session = startPaymentSession(bitcoinUri);
        validatePaymentSession(session);

        final NetworkParameters params = session.getNetworkParameters();
        final PaymentProtocol.Output output = session.getOutputs().get(0);
        final PaymentProtocol.PkiVerificationData vData = session.pkiVerificationData;
        final String address = new Script(output.scriptData)
                .getToAddress(params, false)
                .toString();

        if (! networkParameters.equals(params)) {
            throw new InvalidPaymentRequestError("Payment link from wrong network");
        }

        if (! ValidationHelpers.isValidAddress(networkParameters, address)) {
            throw new InvalidPaymentRequestError("Request returned invalid address");
        }

        return toBitcoinUriContent(
                address,
                output.amount,
                session.getMemo(),
                (vData != null) ? vData.displayName : null
        );
    }

    private void validatePaymentSession(PaymentSession session) {
        if (session.verifyPki() == null && !Globals.INSTANCE.isDebugBuild()) {
            throw new InvalidPaymentRequestError("Unable to verify PKI");
        }

        if (session.isExpired()) {
            throw new InvalidPaymentRequestError("Payment link expired");
        }

        if (session.getPaymentDetails().getOutputsCount() != 1) {
            throw new InvalidPaymentRequestError("Request has output count != 1");
        }
    }

    private PaymentSession startPaymentSession(BitcoinUri bitcoinUri) {
        try {
            return PaymentSession.createFromUrl(bitcoinUri.getPaymentRequestUrl()).get();

        } catch (PaymentProtocolException e) {
            throw new InvalidPaymentRequestError(e);

        } catch (InterruptedException | ExecutionException e) {
            throw new NetworkException(e);
        }
    }

    private BitcoinUriContent toBitcoinUriContent(String address,
                                                  Coin amount,
                                                  String memo,
                                                  String merchant) {

        if (address == null) {
            throw new InvalidPaymentRequestError();
        }

        return new BitcoinUriContent(
                address,
                (amount != null) ? amount.longValue() : null,
                memo,
                merchant
        );
    }
}
