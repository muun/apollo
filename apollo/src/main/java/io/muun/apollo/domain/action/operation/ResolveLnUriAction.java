package io.muun.apollo.domain.action.operation;

import io.muun.apollo.data.net.HoustonClient;
import io.muun.apollo.data.preferences.FeeWindowRepository;
import io.muun.apollo.data.preferences.KeysRepository;
import io.muun.apollo.domain.action.base.BaseAsyncAction1;
import io.muun.apollo.domain.errors.InvalidSwapException;
import io.muun.apollo.domain.errors.InvoiceExpiredException;
import io.muun.apollo.domain.errors.InvoiceMissingAmountException;
import io.muun.apollo.domain.libwallet.Invoice;
import io.muun.apollo.domain.libwallet.LibwalletBridge;
import io.muun.apollo.domain.model.FeeWindow;
import io.muun.apollo.domain.model.OperationUri;
import io.muun.apollo.domain.model.PaymentRequest;
import io.muun.apollo.domain.model.SubmarineSwap;
import io.muun.apollo.domain.model.SubmarineSwapRequest;
import io.muun.apollo.domain.utils.DateUtils;
import io.muun.common.crypto.hd.PublicKeyPair;
import io.muun.common.utils.BitcoinUtils;

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

    private static final int SWAP_V2_CONF_TARGET = 250; // Approx 2 days

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
        final Invoice invoice = LibwalletBridge.decodeInvoice(network, uri.getLnInvoice().get());

        if (invoice.getAmountInSat() == null) {
            throw new InvoiceMissingAmountException(invoice.getOriginal());
        }

        if (invoice.getExpirationTime().isBefore(DateUtils.now())) {
            throw new InvoiceExpiredException(invoice.getOriginal());
        }

        return prepareSwap(buildSubmarineSwapRequest(invoice))
                .map(swap -> buildPaymentRequest(invoice, swap));
    }

    private SubmarineSwapRequest buildSubmarineSwapRequest(Invoice invoice) {
        return new SubmarineSwapRequest(
                invoice.getOriginal(),
                calculateExpirationTimeInBlocks(invoice.getAmountInSat())
        );
    }

    private int calculateExpirationTimeInBlocks(long amountInSats) {
        if (amountInSats > DYNAMIC_TARGET_THRESHOLD_IN_SATS) {
            return BLOCKS_IN_A_DAY;
        }

        return (int) (BLOCKS_IN_A_DAY * (DAYS_IN_A_WEEK
                - (DAYS_IN_A_WEEK - 1) * amountInSats / DYNAMIC_TARGET_THRESHOLD_IN_SATS));
    }

    @NonNull
    private PaymentRequest buildPaymentRequest(Invoice invoice, SubmarineSwap swap) {
        final FeeWindow feeWindow = feeWindowRepository.fetchOne();
        final MonetaryAmount amount = getInvoiceAmount(invoice);

        if (! swap.isLend()) {
            validateNonLendSwap(invoice, swap);
        }

        if (!DateUtils.isEqual(invoice.getExpirationTime(), swap.getExpiresAt())) {
            throw new InvalidSwapException(swap.houstonUuid);
        }

        return PaymentRequest.toLnInvoice(
                invoice,
                amount,
                invoice.getDescription(),
                swap,
                getFeeRate(swap, feeWindow)
        );
    }

    private void validateNonLendSwap(Invoice invoice, SubmarineSwap swap) {
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

    private double getFeeRate(SubmarineSwap swap, FeeWindow feeWindow) {
        if (swap.getFundingOutput().getConfirmationsNeeded() == 0) {
            return feeWindow.getMinimumFeeInSatoshisPerByte(SWAP_V2_CONF_TARGET);
        }

        return feeWindow.getFastestFeeInSatoshisPerByte();
    }

    @NonNull
    private MonetaryAmount getInvoiceAmount(Invoice invoice) {
        return BitcoinUtils.satoshisToBitcoins(invoice.getAmountInSat());
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
