package io.muun.apollo.domain.action;

import io.muun.apollo.BuildConfig;
import io.muun.apollo.data.logging.Logger;
import io.muun.apollo.data.os.ClipboardProvider;
import io.muun.apollo.domain.errors.InvalidPaymentRequestError;
import io.muun.apollo.domain.model.BitcoinUriContent;

import com.google.common.util.concurrent.ListenableFuture;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.protocols.payments.PaymentProtocol;
import org.bitcoinj.protocols.payments.PaymentProtocolException;
import org.bitcoinj.protocols.payments.PaymentSession;
import org.bitcoinj.script.Script;
import org.bitcoinj.uri.BitcoinURI;
import org.bitcoinj.uri.BitcoinURIParseException;
import rx.Observable;
import rx.internal.producers.SingleDelayedProducer;

import java.util.concurrent.Executor;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.validation.constraints.NotNull;


@Singleton
public class BitcoinActions {

    private final ClipboardProvider clipboardProvider;

    private final AddressActions addressActions;

    private final NetworkParameters networkParameters;

    private final Executor executor;

    /**
     * Constructor.
     */
    @Inject
    public BitcoinActions(ClipboardProvider clipboardProvider,
                          AddressActions addressActions,
                          NetworkParameters networkParameters,
                          Executor executor) {

        this.clipboardProvider = clipboardProvider;
        this.addressActions = addressActions;
        this.networkParameters = networkParameters;
        this.executor = executor;

    }

    /**
     * Copy a new address to the clipboard.
     */
    public void copyAddressToClipboard() {

        final String address = addressActions.getExternalAddress();
        clipboardProvider.copy("Bitcoin address", address);
    }

    /**
     * Read a String and create a PaymentInfo if able.
     */
    public Observable<BitcoinUriContent> getBitcoinUriContent(@NotNull String text) {

        return Observable.defer(() -> {
            final BitcoinURI bitcoinUri = parseBitcoinUri(text);

            return getPaymentRequestBip72(bitcoinUri)
                    .onErrorReturn(throwable -> {
                        Logger.debug(throwable, "Falling back to BIP-21");
                        return getPaymentRequestBip21(bitcoinUri);
                    });
        });
    }

    private BitcoinURI parseBitcoinUri(String text) {
        try {
            return new BitcoinURI(networkParameters, text);

        } catch (BitcoinURIParseException e) {
            throw new InvalidPaymentRequestError(e);
        }
    }

    private BitcoinUriContent getPaymentRequestBip21(BitcoinURI bitcoinUri) {
        return toBitcoinUriContent(
                bitcoinUri.getAddress(),
                bitcoinUri.getAmount(),
                bitcoinUri.getMessage(),
                bitcoinUri.getLabel()
        );
    }

    private Observable<BitcoinUriContent> getPaymentRequestBip72(BitcoinURI bitcoinUri) {
        return startPaymentSession(bitcoinUri)
                .map(session -> {
                    validatePaymentSession(session);

                    final NetworkParameters netParams = session.getNetworkParameters();
                    final PaymentProtocol.Output output = session.getOutputs().get(0);
                    final PaymentProtocol.PkiVerificationData vData = session.pkiVerificationData;

                    return toBitcoinUriContent(
                            new Script(output.scriptData).getToAddress(netParams, false),
                            output.amount,
                            session.getMemo(),
                            (vData != null) ? vData.displayName : null
                    );
                });
    }

    private void validatePaymentSession(PaymentSession session) {
        if (session.verifyPki() == null && !BuildConfig.DEBUG) {
            throw new InvalidPaymentRequestError("Unable to verify PKI");
        }

        if (session.isExpired()) {
            throw new InvalidPaymentRequestError("Payment link expired");
        }

        if (session.getPaymentDetails().getOutputsCount() != 1) {
            throw new InvalidPaymentRequestError("Request has output count != 1");
        }
    }

    private Observable<PaymentSession> startPaymentSession(BitcoinURI bitcoinUri) {
        return Observable.just(bitcoinUri)
                .map(uri -> {
                    try {
                        return PaymentSession.createFromBitcoinUri(uri);

                    } catch (PaymentProtocolException e) {
                        throw new InvalidPaymentRequestError(e);
                    }
                })
                .flatMap(this::futureToObservable);
    }

    private BitcoinUriContent toBitcoinUriContent(Address address,
                                                  Coin amount,
                                                  String memo,
                                                  String merchant) {

        if (address == null) {
            throw new InvalidPaymentRequestError();
        }

        return new BitcoinUriContent(
                address.toString(),
                (amount != null) ? amount.longValue() : null,
                memo,
                merchant
        );
    }

    /**
     * Transform a Guava's ListenableFuture into an Observable.
     *
     * <p>Got this trick from:
     * https://github.com/ReactiveX/RxJavaGuava/blob/master/src/main/java/rx/observable/ListenableFutureObservable.java#L67
     */
    private <T> Observable<T> futureToObservable(ListenableFuture<T> future) {

        return Observable.create(subscriber -> {

            final SingleDelayedProducer<T> producer = new SingleDelayedProducer<>(subscriber);
            subscriber.setProducer(producer);

            future.addListener(() -> {
                try {
                    final T t = future.get();
                    producer.setValue(t);
                } catch (Exception e) {
                    subscriber.onError(e);
                }
            }, executor);
        });
    }
}
