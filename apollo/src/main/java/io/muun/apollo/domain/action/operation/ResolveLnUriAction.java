package io.muun.apollo.domain.action.operation;

import io.muun.apollo.data.net.HoustonClient;
import io.muun.apollo.data.preferences.KeysRepository;
import io.muun.apollo.domain.action.base.BaseAsyncAction1;
import io.muun.apollo.domain.errors.InvalidInvoiceAmountException;
import io.muun.apollo.domain.errors.InvalidSwapException;
import io.muun.apollo.domain.errors.InvoiceExpiredException;
import io.muun.apollo.domain.model.OperationUri;
import io.muun.apollo.domain.model.PaymentRequest;
import io.muun.apollo.domain.model.SubmarineSwap;
import io.muun.apollo.domain.utils.DateUtils;
import io.muun.common.crypto.hd.PublicKeyPair;
import io.muun.common.utils.BitcoinUtils;
import io.muun.common.utils.LnInvoice;

import android.support.annotation.NonNull;
import android.support.annotation.VisibleForTesting;
import org.bitcoinj.core.NetworkParameters;
import rx.Observable;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.money.MonetaryAmount;

@Singleton
public class ResolveLnUriAction extends BaseAsyncAction1<OperationUri, PaymentRequest> {

    private final NetworkParameters network;
    private final HoustonClient houstonClient;
    private final KeysRepository keysRepository;

    /**
     * Resolves a LightningNetwork OperationUri.
     */
    @Inject
    ResolveLnUriAction(NetworkParameters network,
                       HoustonClient houstonClient,
                       KeysRepository keysRepository) {
        this.network = network;
        this.houstonClient = houstonClient;
        this.keysRepository = keysRepository;
    }

    @Override
    public Observable<PaymentRequest> action(OperationUri operationUri) {
        return resolveLnUri(operationUri);
    }

    private Observable<PaymentRequest> resolveLnUri(OperationUri uri) {
        final LnInvoice invoice = LnInvoice.decode(network, uri.getHost());

        if (invoice.amount == null) {
            throw new InvalidInvoiceAmountException(invoice.original);
        }

        if (invoice.getExpirationTime().isBefore(DateUtils.now())) {
            throw new InvoiceExpiredException(invoice.original);
        }

        return prepareSwap(invoice.original)
                .map(swap -> buildPaymentRequest(invoice, swap));
    }

    @NonNull
    private PaymentRequest buildPaymentRequest(LnInvoice invoice, SubmarineSwap swap) {

        final MonetaryAmount amount = getInvoiceAmount(invoice);

        final Long paymentAmount = swap.fundingOutput.outputAmountInSatoshis
                - swap.sweepFeeInSatoshis
                - swap.lightningFeeInSatoshis;

        if (invoice.amount.amountInSatoshis != paymentAmount) {
            throw new InvalidSwapException(swap.houstonUuid);
        }

        if (!DateUtils.isEqual(invoice.getExpirationTime(), swap.expiresAt)) {
            throw new InvalidSwapException(swap.houstonUuid);
        }

        if (invoice.addresses.isEmpty()) {
            return PaymentRequest.toLnInvoice(invoice, amount, invoice.description, swap);

        } else {
            // NOTE: as long as we use submarine swaps, this is cheaper:
            return PaymentRequest.toAddress(invoice.addresses.get(0), amount, invoice.description);
        }
    }

    @NonNull
    private MonetaryAmount getInvoiceAmount(LnInvoice invoice) {
        return BitcoinUtils.satoshisToBitcoins(invoice.amount.amountInSatoshis);
    }

    /**
     * Create a new Submarine Swap.
     */
    @VisibleForTesting
    public Observable<SubmarineSwap> prepareSwap(String invoice) {
        final PublicKeyPair basePublicKeyPair = keysRepository.getBasePublicKeyPair();
        return houstonClient.prepareSubmarineSwap(invoice, basePublicKeyPair, network);
    }
}
