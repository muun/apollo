package io.muun.apollo.domain.action.operation;

import io.muun.apollo.data.net.HoustonClient;
import io.muun.apollo.data.preferences.FeeWindowRepository;
import io.muun.apollo.data.preferences.KeysRepository;
import io.muun.apollo.domain.action.base.BaseAsyncAction1;
import io.muun.apollo.domain.errors.InvalidSwapException;
import io.muun.apollo.domain.errors.InvoiceExpiredException;
import io.muun.apollo.domain.libwallet.DecodedInvoice;
import io.muun.apollo.domain.libwallet.LibwalletBridge;
import io.muun.apollo.domain.model.FeeWindow;
import io.muun.apollo.domain.model.OperationUri;
import io.muun.apollo.domain.model.PaymentRequest;
import io.muun.apollo.domain.model.SubmarineSwap;
import io.muun.apollo.domain.model.SubmarineSwapRequest;
import io.muun.apollo.domain.utils.DateUtils;
import io.muun.common.crypto.hd.PublicKeyPair;
import io.muun.common.utils.BitcoinUtils;
import io.muun.common.utils.Preconditions;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
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
    private final FeeWindowRepository feeWindowRepository;

    private static final int BLOCKS_IN_A_DAY = 24 * 6; // this is 144
    private static final int DAYS_IN_A_WEEK = 7;
    private static final int DYNAMIC_TARGET_THRESHOLD_IN_SATS = 150_000;

    /**
     * Resolves a LightningNetwork OperationUri.
     */
    @Inject
    ResolveLnUriAction(NetworkParameters network,
                       HoustonClient houstonClient,
                       KeysRepository keysRepository,
                       FeeWindowRepository feeWindowRepository) {
        this.network = network;
        this.houstonClient = houstonClient;
        this.keysRepository = keysRepository;
        this.feeWindowRepository = feeWindowRepository;
    }

    @Override
    public Observable<PaymentRequest> action(OperationUri operationUri) {
        return resolveLnUri(operationUri);
    }

    private Observable<PaymentRequest> resolveLnUri(OperationUri uri) {
        final DecodedInvoice invoice =
                LibwalletBridge.decodeInvoice(network, uri.getLnInvoice().get());

        if (invoice.getExpirationTime().isBefore(DateUtils.now())) {
            throw new InvoiceExpiredException(invoice.getOriginal());
        }

        return prepareSwap(buildSubmarineSwapRequest(invoice))
                .map(swap -> buildPaymentRequest(invoice, swap));
    }

    private SubmarineSwapRequest buildSubmarineSwapRequest(DecodedInvoice invoice) {
        // We used to care a lot about this number for v1 swaps since it was the refund time
        // With swaps v2 we have collaborative refunds so we don't quite care and go for the max
        return new SubmarineSwapRequest(
                invoice.getOriginal(),
                BLOCKS_IN_A_DAY * DAYS_IN_A_WEEK
        );
    }

    @NonNull
    private PaymentRequest buildPaymentRequest(DecodedInvoice invoice, SubmarineSwap swap) {
        final FeeWindow feeWindow = feeWindowRepository.fetchOne();
        final MonetaryAmount amount = getInvoiceAmount(invoice);

        if (! swap.isLend()) {
            validateNonLendSwap(invoice, swap);
        }

        if (!DateUtils.isEqual(invoice.getExpirationTime(), swap.getExpiresAt())) {
            throw new InvalidSwapException(swap.houstonUuid);
        }

        // For AmountLess Invoices, fee rate is initially unknown
        final Double feeRate = invoice.getAmountInSat() != null ? feeWindow.getFeeRate(swap) : null;

        return PaymentRequest.toLnInvoice(
                invoice,
                amount,
                invoice.getDescription(),
                swap,
                feeRate
        );
    }

    private void validateNonLendSwap(DecodedInvoice invoice, SubmarineSwap swap) {

        if (invoice.getAmountInSat() == null) {
            return; // Do not perform this validation for AmountLess Invoices
        }

        Preconditions.checkNotNull(swap.getFundingOutput().getOutputAmountInSatoshis());
        Preconditions.checkNotNull(swap.getFundingOutput().getDebtAmountInSatoshis());
        Preconditions.checkNotNull(swap.getFundingOutput().getConfirmationsNeeded());
        Preconditions.checkNotNull(swap.getFees());

        final long actualOutputAmount = swap.getFundingOutput().getOutputAmountInSatoshis();

        long expectedOutputAmount = invoice.getAmountInSat()
                + swap.getFees().getSweepInSats()
                + swap.getFees().getLightningInSats();

        if (swap.isCollect()) {
            expectedOutputAmount += swap.getFundingOutput().getDebtAmountInSatoshis();
        }

        if (actualOutputAmount != expectedOutputAmount) {
            throw new InvalidSwapException(swap.houstonUuid);
        }
    }

    private MonetaryAmount getInvoiceAmount(DecodedInvoice invoice) {

        if (invoice.getAmountInSat() != null) {
            return BitcoinUtils.satoshisToBitcoins(invoice.getAmountInSat());
        }

        return null;
    }

    /**
     * Create a new Submarine Swap.
     */
    @VisibleForTesting
    public Observable<SubmarineSwap> prepareSwap(SubmarineSwapRequest request) {
        final PublicKeyPair basePublicKeyPair = keysRepository.getBasePublicKeyPair();
        return houstonClient.createSubmarineSwap(request, basePublicKeyPair, network);
    }
}
