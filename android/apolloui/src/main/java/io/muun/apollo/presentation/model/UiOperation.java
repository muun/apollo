package io.muun.apollo.presentation.model;

import io.muun.apollo.R;
import io.muun.apollo.data.external.Globals;
import io.muun.apollo.domain.libwallet.DecodedInvoice;
import io.muun.apollo.domain.libwallet.Invoice;
import io.muun.apollo.domain.model.CurrencyDisplayMode;
import io.muun.apollo.domain.model.Operation;
import io.muun.apollo.domain.model.SubmarineSwapFundingOutput;
import io.muun.apollo.domain.utils.ExtensionsKt;
import io.muun.apollo.presentation.ui.helper.BitcoinHelper;
import io.muun.apollo.presentation.ui.helper.MoneyHelper;
import io.muun.apollo.presentation.ui.utils.LinkBuilder;
import io.muun.apollo.presentation.ui.utils.UiUtils;
import io.muun.apollo.presentation.ui.view.RichText;
import io.muun.common.bitcoinj.BlockHelpers;
import io.muun.common.bitcoinj.NetworkParametersHelper;
import io.muun.common.crypto.schemes.TransactionSchemeSubmarineSwapV2;
import io.muun.common.exception.MissingCaseError;
import io.muun.common.model.OperationStatus;
import io.muun.common.utils.Encodings;
import io.muun.common.utils.Preconditions;

import android.content.Context;
import androidx.core.content.ContextCompat;
import org.bitcoinj.core.NetworkParameters;

import java.util.regex.Pattern;
import javax.annotation.Nullable;
import javax.money.MonetaryAmount;
import javax.validation.constraints.NotNull;

import static io.muun.common.utils.Preconditions.checkNotNull;

public abstract class UiOperation {

    private static final Pattern HEX_NUMBER = Pattern.compile("^[a-fA-F0-9]+$");
    private static final double TARGET_BLOCK_CONFIRMATION_CERTAINTY = 0.75;

    final Operation operation;

    private final NetworkParameters networkParameters;
    private final LinkBuilder linkBuilder;
    private final CurrencyDisplayMode currencyDisplayMode;

    /**
     * Build a UiOperation from an ExternalOperation.
     */
    public static UiOperation fromOperation(Operation operation,
                                            NetworkParameters networkParameters,
                                            LinkBuilder linkBuilder,
                                            CurrencyDisplayMode currencyDisplayMode) {

        if (operation.isExternal) {
            return new ExternalOperation(
                    operation,
                    networkParameters,
                    linkBuilder,
                    currencyDisplayMode
            );
        } else {
            return new InternalOperation(
                    operation,
                    networkParameters,
                    linkBuilder,
                    currencyDisplayMode
            );
        }
    }

    /**
     * Constructor.
     */
    public UiOperation(@NotNull Operation operation,
                       @NotNull NetworkParameters networkParameters,
                       @NotNull LinkBuilder linkBuilder,
                       @NotNull CurrencyDisplayMode currencyDisplayMode) {

        checkNotNull(operation);
        checkNotNull(networkParameters);

        this.networkParameters = networkParameters;
        this.operation = operation;
        this.linkBuilder = linkBuilder;
        this.currencyDisplayMode = currencyDisplayMode;
    }

    /**
     * Get the id of the underlying operation.
     */
    public Long getId() {
        return operation.getId();
    }

    /**
     * Get the title line as formatted RichText.
     */
    public final CharSequence getFormattedTitle(Context context) {
        return getFormattedTitle(context, false);
    }

    /**
     * Get the title line as formatted RichText.
     */
    public abstract CharSequence getFormattedTitle(Context context, boolean shortName);

    /**
     * Get the description as formatted RichText.
     */
    public CharSequence getFormattedDescription(Context context) {
        if (operation.description != null) {
            return operation.description;
        } else if (isIncomingSwap()) {
            return context.getString(R.string.history_external_incoming_swap_description);
        } else {
            return context.getString(R.string.history_external_incoming_operation_description);
        }
    }

    /**
     * Get the amount as formatted RichText.
     */
    public final CharSequence getFormattedDisplayAmount(Context context) {
        return getFormattedDisplayAmount(context, true);
    }

    /**
     * Get the amount as formatted RichText.
     */
    public CharSequence getFormattedDisplayAmount(Context context, boolean showSign) {
        final String text;
        final int colorId;

        if (isCyclical()) {
            text = getInputAmount();
            colorId = R.color.text_secondary_color;

        } else {
            text = showSign ? getSignedInputAmount() : getInputAmount();
            colorId = isIncoming() ? R.color.green : R.color.text_secondary_color;
        }

        return new RichText(text).setForegroundColor(ContextCompat.getColor(context, colorId));
    }

    /**
     * Get the status as formatted RichText.
     */
    public CharSequence getFormattedStatus(Context context) {

        if (isCompleted()) {
            return getFormattedCompletedStatus(context);

        } else if (isPending()) {

            if (isIncoming() && isRbf()) {
                return getFormattedRbfStatus(context);
            } else {
                return getFormattedPendingStatus(context);
            }

        } else if (isFailed()) {
            return getFormattedFailedStatus(context);

        } else {
            throw new MissingCaseError(operation.status);
        }
    }

    private RichText getFormattedRbfStatus(Context context) {
        final String text = context.getString(R.string.operation_rbf);
        final int color = ContextCompat.getColor(context, R.color.rbf_color);

        return new RichText(text).setForegroundColor(color).setBold();
    }

    private RichText getFormattedPendingStatus(Context context) {
        final int textResId = R.string.operation_pending;
        final String text = context.getString(textResId);
        final int color = ContextCompat.getColor(context, R.color.pending_color);

        return new RichText(text).setForegroundColor(color).setBold();
    }

    private RichText getFormattedCompletedStatus(Context context) {
        final String text = context.getString(R.string.operation_completed);
        final int color = ContextCompat.getColor(context, R.color.green);

        return new RichText(text).setForegroundColor(color).setBold();
    }

    private RichText getFormattedFailedStatus(Context context) {
        final String text = context.getString(R.string.operation_failed);
        final int color = ContextCompat.getColor(context, R.color.error_color);

        return new RichText(text).setForegroundColor(color).setBold();
    }

    /**
     * Get the input amount without sign, to be displayed in the operation detail.
     */
    private String getInputAmount() {
        return MoneyHelper.formatLongMonetaryAmount(
                operation.amount.inInputCurrency,
                currencyDisplayMode
        );
    }

    /**
     * Get the signed input amount, to be displayed in the history's items.
     */
    private String getSignedInputAmount() {

        return String.format(
                isIncoming() ? "+ %s" : "- %s",
                MoneyHelper.formatShortMonetaryAmount(
                        operation.amount.inInputCurrency,
                        currencyDisplayMode
                )
        );
    }

    /**
     * Get the amount in local currency and bitcoin, to be displayed in the operation detail.
     */
    public String getDetailedAmount() {
        return getFormattedAmount(operation.amount.inSatoshis, operation.amount.inPrimaryCurrency);
    }

    /**
     * Get the miner fee in local currency and bitcoin, to be displayed in the operation detail.
     */
    public String getDetailedFee() {

        Long feeInSats = operation.fee.inSatoshis;
        MonetaryAmount feeInPrimaryCurr = operation.fee.inPrimaryCurrency;

        // If operation is a swap we display both funding and sweep tx as the operation fee
        if (operation.swap != null) {

            long totalFeeInSats = feeInSats + operation.swap.getFees().getLightningInSats();

            // LEND swap have no associated on-chain tx, so sweepFee does not apply
            if (!operation.swap.isLend()) {
                totalFeeInSats += operation.swap.getFees().getSweepInSats();
            }

            // As we don't have the value in primary currency for some of the fees involved in a
            // swap, we use a Rule of 3 to calculate their value in primary currency using amount as
            // a reference (we know the amount in sats/primaryCurrency and it's never zero)
            feeInSats = totalFeeInSats;
            feeInPrimaryCurr = UiUtils.convertWithSameRate(
                    totalFeeInSats,
                    operation.amount.inSatoshis,
                    operation.amount.inPrimaryCurrency
            );
        }

        return getFormattedAmount(feeInSats, feeInPrimaryCurr);
    }

    private String getFormattedAmount(long amountInSatoshis, MonetaryAmount amountInPrimaryCurr) {

        if (MoneyHelper.isBtc(amountInPrimaryCurr)) {
            return String.format(
                    "%s",
                    BitcoinHelper.formatLongBitcoinAmount(amountInSatoshis, currencyDisplayMode)
            );
        } else {

            return String.format(
                    "%s (%s)",
                    BitcoinHelper.formatLongBitcoinAmount(amountInSatoshis, currencyDisplayMode),
                    MoneyHelper.formatLongMonetaryAmount(amountInPrimaryCurr, currencyDisplayMode)
            );
        }
    }

    /**
     * Get the network fee in BTC copyable string.
     */
    public String getCopyableNetworkFee() {
        final long feeInSats = operation.fee.inSatoshis;
        return String.format(
                "%s",
                BitcoinHelper.formatLongBitcoinAmount(feeInSats, currencyDisplayMode)
        );
    }

    /**
     * Get the amount in BTC copyable string.
     */
    public String getCopyableAmount() {
        final long amountInSats = operation.amount.inSatoshis;
        return String.format(
                "%s",
                BitcoinHelper.formatLongBitcoinAmount(amountInSats, currencyDisplayMode)
        );
    }

    /**
     * Get the number of confirmations, to be displayed in the operation detail.
     */
    public String getConfirmations() {

        final int settlementNumber = NetworkParametersHelper.getSettlementNumber(networkParameters);

        if (operation.confirmations < settlementNumber) {
            return String.valueOf(operation.confirmations);
        }

        return settlementNumber + "+";
    }

    public String getTransactionId() {
        return operation.hash;
    }

    /**
     * Get a link to the transaction detail in an external block explorer.
     */
    public CharSequence getFormattedTransactionId(Context context) {
        if (operation.hash == null || !HEX_NUMBER.matcher(operation.hash).find()) {
            return context.getString(R.string.not_available);
        }

        return linkBuilder.transactionLink(operation.hash);
    }

    /**
     * Get a link to the raw Bitcoin address that received this payment in a block explorer.
     */
    public CharSequence getFormattedReceiverAddress(Context context) {
        if (operation.receiverAddress == null) {
            return context.getString(R.string.not_available);
        }

        return linkBuilder.addressLink(operation.receiverAddress);
    }

    /**
     * Get the creation date, to be displayed in the history's items.
     */
    public String getFormattedDate() {
        return UiUtils.getFormattedDate(operation.getLocalizedCreationDate());
    }

    /**
     * Get the creation date and time, to be displayed in the operation detail.
     */
    public String getFormattedDateTime(Context context) {
        return UiUtils.getLongFormattedDate(context, operation.getLocalizedCreationDate());
    }

    /**
     * Return the receiver address of the operation.
     */
    public String getReceiverAddress() {
        if (operation.receiverAddress == null) {
            return "";
        }

        return operation.receiverAddress;
    }

    public boolean isExternal() {
        return operation.isExternal;
    }

    public boolean isIncoming() {
        return operation.isIncoming();
    }

    public boolean isCyclical() {
        return operation.isCyclical();
    }

    public boolean isPending() {
        return operation.isPending();
    }

    public boolean isCompleted() {
        return operation.isCompleted();
    }

    public boolean isFailed() {
        return operation.isFailed();
    }

    public boolean isRbf() {
        return operation.isRbf;
    }

    /**
     * Get the description for this operation.
     */
    @Nullable
    public String getDescription() {
        return operation.description;
    }

    /**
     * Get the URI for this operation's picture.
     */
    @Nullable
    public abstract String getPictureUri(Context context);

    public boolean isSwap() {
        return operation.swap != null;
    }

    public boolean is0ConfSwap() {
        return isSwap() && operation.swap.getFundingOutput().getConfirmationsNeeded() == 0;
    }

    public String getSwapInvoice() {
        return (operation.swap == null) ? "" : operation.swap.getInvoice();
    }

    /**
     * Get the receiving LN node data, in a clickable link to an explorer.
     */
    public CharSequence getSwapReceiverLink() {
        if (operation.swap == null) {
            return "";
        }

        return linkBuilder.lightningNodeLink(operation.swap.getReceiver());
    }

    public String getPreimage() {
        if (operation.swap != null) {
            return operation.swap.getPreimageInHex();

        } else if (operation.incomingSwap != null) {
            final byte[] preimage = operation.incomingSwap.getPreimage();
            return preimage != null ? Encodings.bytesToHex(preimage) : "";

        } else {
            return "";
        }
    }

    @Nullable
    public String getInvoiceDescription() {
        if (operation.metadata == null || ExtensionsKt.isEmpty(operation.metadata.invoice)) {
            return null;
        }

        final DecodedInvoice decodedInvoice = Invoice.INSTANCE.decodeInvoice(
                Globals.INSTANCE.getNetwork(),
                operation.metadata.invoice
        );

        return decodedInvoice.getDescription();
    }

    @Nullable
    public String getLnUrlSender() {
        return operation.metadata != null ? operation.metadata.lnurlSender : null;
    }

    public String getPaymentHash() {
        if (operation.swap != null) {
            return operation.swap.getFundingOutput().getServerPaymentHashInHex();

        } else if (operation.incomingSwap != null) {
            return Encodings.bytesToHex(operation.incomingSwap.getPaymentHash());

        } else {
            return "";
        }
    }

    public OperationStatus getOperationStatus() {
        return operation.status;
    }

    /**
     * Get refund message for a failed swap.
     */
    public String getRefundMessage(Context context, int blockchainHeight) {
        Preconditions.checkNotNull(operation.swap);

        final SubmarineSwapFundingOutput fundingOutput = operation.swap.getFundingOutput();

        if (fundingOutput.getScriptVersion() == TransactionSchemeSubmarineSwapV2.ADDRESS_VERSION) {
            return context.getString(R.string.operation_swap_expired_desc);
        }

        return getRefundMessageForSwapV1(context, blockchainHeight);
    }

    private String getRefundMessageForSwapV1(Context context, int blockchainHeight) {

        Preconditions.checkNotNull(operation.swap);
        Preconditions.checkNotNull(operation.swap.getFundingOutput().getUserLockTime());

        final SubmarineSwapFundingOutput fundingOutput = operation.swap.getFundingOutput();
        final int blocksUntilRefund = fundingOutput.getUserLockTime() - blockchainHeight;

        if (blocksUntilRefund <= 0) { // If userLockTime is reached, swap's already expired/refunded
            return context.getString(R.string.operation_swap_expired_desc);
        }

        final int seconds = BlockHelpers.timeInSecsForBlocksWithCertainty(blocksUntilRefund,
                TARGET_BLOCK_CONFIRMATION_CERTAINTY);
        final int hours = seconds / 60 / 60;

        return context.getString(
                R.string.operation_swap_failed_desc,
                hours,
                blocksUntilRefund
        );
    }

    public boolean isIncomingSwap() {
        return operation.isIncomingSwap();
    }
}
