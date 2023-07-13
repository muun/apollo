package io.muun.apollo.presentation.model

import android.content.Context
import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import io.muun.apollo.R
import io.muun.apollo.data.external.Globals
import io.muun.apollo.domain.libwallet.Invoice.decodeInvoice
import io.muun.apollo.domain.model.BitcoinUnit
import io.muun.apollo.domain.model.Operation
import io.muun.apollo.domain.utils.isEmpty
import io.muun.apollo.domain.utils.locale
import io.muun.apollo.presentation.ui.helper.BitcoinHelper
import io.muun.apollo.presentation.ui.helper.MoneyHelper
import io.muun.apollo.presentation.ui.helper.isBtc
import io.muun.apollo.presentation.ui.utils.LinkBuilder
import io.muun.apollo.presentation.ui.utils.UiUtils
import io.muun.apollo.presentation.ui.view.RichText
import io.muun.common.bitcoinj.BlockHelpers
import io.muun.common.bitcoinj.NetworkParametersHelper
import io.muun.common.exception.MissingCaseError
import io.muun.common.model.OperationStatus
import io.muun.common.utils.Preconditions
import libwallet.Libwallet
import java.util.*
import java.util.regex.Pattern
import javax.money.MonetaryAmount

abstract class UiOperation(
    @JvmField internal val operation: Operation,
    private val linkBuilder: LinkBuilder,
    private val bitcoinUnit: BitcoinUnit,
    private val context: Context,
) {

    companion object {
        private val HEX_NUMBER = Pattern.compile("^[a-fA-F0-9]+$")
        private const val TARGET_BLOCK_CONFIRMATION_CERTAINTY = 0.75
        private val networkParams = Globals.INSTANCE.network

        /**
         * Build a UiOperation from an ExternalOperation.
         */
        @JvmStatic
        fun fromOperation(
            operation: Operation,
            linkBuilder: LinkBuilder,
            bitcoinUnit: BitcoinUnit,
            context: Context,
        ): UiOperation {
            return if (operation.isExternal) {
                ExternalOperation(operation, linkBuilder, bitcoinUnit, context)
            } else {
                InternalOperation(operation, linkBuilder, bitcoinUnit, context)
            }
        }
    }

    val locale: Locale
        get() = context.locale()

    /**
     * Get the id of the underlying operation.
     */
    val id: Long?
        get() = operation.id

    val transactionId: String?
        get() = operation.hash

    val operationStatus: OperationStatus
        get() = operation.status

    /**
     * Get the amount in local currency and bitcoin, to be displayed in the operation detail.
     */
    val detailedAmount: String
        get() = getFormattedAmount(operation.amount.inSatoshis, operation.amount.inPrimaryCurrency)

    /**
     * Get the miner fee in local currency and bitcoin, to be displayed in the operation detail.
     */
    val detailedFee: String
        get() {
            val (feeInSats, feeInPrimaryCurr) = calculateFee()
            return getFormattedAmount(`feeInSats`, feeInPrimaryCurr)
        }

    /**
     * Get the network fee in BTC copyable string.
     */
    val copyableNetworkFee: String
        get() {
            val (feeInSats, _) = calculateFee()
            return BitcoinHelper.formatLongBitcoinAmount(feeInSats, bitcoinUnit, locale)
        }

    private fun calculateFee(): Pair<Long, MonetaryAmount> {
        var feeInSats = operation.fee.inSatoshis
        var feeInPrimaryCurr = operation.fee.inPrimaryCurrency

        // If operation is a swap we display both funding and sweep tx as the operation fee
        if (operation.swap != null) {
            val totalFeeInSats = feeInSats + (operation.swap!!.totalFeesInSat() ?: 0)

            // As we don't have the value in primary currency for some of the fees involved in a
            // swap, we use a Rule of 3 to calculate their value in primary currency using amount as
            // a reference (we know the amount in sats/primaryCurrency and it's never zero)
            feeInSats = totalFeeInSats
            feeInPrimaryCurr = UiUtils.convertWithSameRate(
                totalFeeInSats,
                operation.amount.inSatoshis,
                operation.amount.inPrimaryCurrency
            )
        }
        return Pair(feeInSats, feeInPrimaryCurr)
    }

    /**
     * Get the amount in BTC copyable string.
     */
    val copyableAmount: String
        get() {
            val amountInSats = operation.amount.inSatoshis
            return BitcoinHelper.formatLongBitcoinAmount(amountInSats, bitcoinUnit, locale)
        }

    /**
     * Get the number of confirmations, to be displayed in the operation detail.
     */
    val confirmations: String
        get() {
            val settlementNumber = NetworkParametersHelper.getSettlementNumber(networkParams)
            return if (operation.confirmations < settlementNumber) {
                operation.confirmations.toString()
            } else {
                "$settlementNumber+"
            }
        }

    /**
     * Get the creation date, to be displayed in the history's items.
     */
    val formattedDate: String
        get() = UiUtils.getFormattedDate(operation.localizedCreationDate)

    /**
     * Return the receiver address of the operation.
     */
    val receiverAddress: String?
        get() = if (operation.receiverAddress == null) {
            ""
        } else {
            operation.receiverAddress
        }

    val isIncoming: Boolean
        get() = operation.isIncoming

    val isCyclical: Boolean
        get() = operation.isCyclical

    val isPending: Boolean
        get() = operation.isPending

    val isCompleted: Boolean
        get() = operation.isCompleted

    val isFailed: Boolean
        get() = operation.isFailed

    val isRbf: Boolean
        get() = operation.isRbf

    val isSwap: Boolean
        get() = operation.swap != null

    val is0ConfSwap: Boolean
        get() = isSwap && operation.swap!!.fundingOutput.confirmationsNeeded == 0

    val swapInvoice: String
        get() = if (operation.swap == null) "" else operation.swap!!.invoice

    val isIncomingSwap: Boolean
        get() = operation.isIncomingSwap

    /**
     * Get the description for this operation.
     */
    val description: String?
        get() = operation.description

    /**
     * Get the receiving LN node data.
     */
    val swapReceiverNodeData: String
        get() = if (operation.swap == null) {
            ""
        } else {
            operation.swap!!.receiver.formattedDestination
        }

    /**
     * Get the receiving LN node alias.
     */
    val swapReceiverAlias: CharSequence
        get() = operation.swap?.receiver?.alias ?: ""

    /**
     * Get the preimage of swap or incomingSwap, if this is a LN payment. Empty String otherwise.
     */
    val preimage: String
        get() = if (operation.swap != null) {
            // 2nd ? is needed to avoid generic kotlin Any?.toString() which prints "null"
            operation.swap!!.preimage?.toString() ?: ""
        } else {
            operation.incomingSwap?.getPreimage()?.toString() ?: ""
        }

    /**
     * Get the paymentHash of swap or incomingSwap, if this is a LN payment. Empty String otherwise.
     */
    val paymentHash: String
        get() = if (operation.swap != null) {
            operation.swap!!.fundingOutput.paymentHash.toString()
        } else {
            // 2nd ? is needed to avoid generic kotlin Any?.toString() which prints "null"
            operation.incomingSwap?.getPaymentHash()?.toString() ?: ""
        }

    val invoiceDescription: String?
        get() {
            if (operation.metadata == null || operation.metadata!!.invoice.isEmpty()) {
                return null
            }

            return decodeInvoice(networkParams, operation.metadata!!.invoice!!).description
        }

    val lnUrlSender: String?
        get() = if (operation.metadata != null) operation.metadata!!.lnurlSender else null

    /**
     * Get the input amount without sign, to be displayed in the operation detail.
     */
    private val inputAmount: String
        get() = getFormattedinputAmount(longFormat = true)

    /**
     * Get the signed input amount, to be displayed in the history's items.
     */
    private val signedInputAmount: String
        get() = "${if (isIncoming) "+" else "-"} ${getFormattedinputAmount(longFormat = false)}"

    /**
     * Get the URI for this operation's picture.
     */
    abstract fun getPictureUri(context: Context): String?

    /**
     * Get the title line as formatted RichText.
     */
    abstract fun getFormattedTitle(context: Context, shortName: Boolean): CharSequence

    /**
     * Get the title line as formatted RichText.
     */
    fun getFormattedTitle(context: Context): CharSequence =
        getFormattedTitle(context, false)

    /**
     * Get the description as formatted RichText.
     */
    fun getFormattedDescription(context: Context): CharSequence? =
        when {
            operation.description != null -> operation.description
            isIncomingSwap -> context.getString(R.string.history_external_incoming_swap_description)
            else -> context.getString(R.string.history_external_incoming_operation_description)
        }

    /**
     * Get the amount as formatted RichText.
     */
    fun getFormattedDisplayAmount(context: Context): CharSequence =
        getFormattedDisplayAmount(context, true)

    /**
     * Get the amount as formatted RichText.
     */
    fun getFormattedDisplayAmount(context: Context, showSign: Boolean): CharSequence {
        val text: String
        val colorId: Int
        if (isCyclical) {
            text = inputAmount
            colorId = R.color.text_secondary_color
        } else {
            text = if (showSign) signedInputAmount else inputAmount
            colorId = if (isIncoming) R.color.green else R.color.text_secondary_color
        }

        return RichText(text).setForegroundColor(ContextCompat.getColor(context, colorId))
    }

    /**
     * Get the status as formatted RichText.
     */
    fun getFormattedStatus(context: Context): CharSequence {
        if (isCompleted) {
            return getFormattedCompletedStatus(context)

        } else if (isPending) {
            if (isIncoming && isRbf) {
                return getFormattedRbfStatus(context)

            } else {
                return getFormattedPendingStatus(context)
            }

        } else if (isFailed) {
            return getFormattedFailedStatus(context)

        } else {
            throw MissingCaseError(operation.status)
        }
    }

    private fun getFormattedRbfStatus(context: Context): RichText =
        boldRichText(context, R.string.operation_rbf, R.color.rbf_color)

    private fun getFormattedPendingStatus(context: Context): RichText =
        boldRichText(context, R.string.operation_pending, R.color.pending_color)

    private fun getFormattedCompletedStatus(context: Context): RichText =
        boldRichText(context, R.string.operation_completed, R.color.green)

    private fun getFormattedFailedStatus(context: Context): RichText =
        boldRichText(context, R.string.operation_failed, R.color.error_color)

    private fun boldRichText(context: Context, @StringRes textId: Int, @ColorRes colorId: Int) =
        RichText(context.getString(textId))
            .setForegroundColor(ContextCompat.getColor(context, colorId))
            .setBold()

    private fun getFormattedinputAmount(longFormat: Boolean) =
        if (longFormat) {
            MoneyHelper.formatLongMonetaryAmount(
                operation.amount.inInputCurrency,
                bitcoinUnit,
                locale
            )
        } else {
            MoneyHelper.formatShortMonetaryAmount(
                operation.amount.inInputCurrency,
                bitcoinUnit,
                locale
            )
        }

    private fun getFormattedAmount(amountInSat: Long, amountInPrimary: MonetaryAmount): String =
        if (amountInPrimary.isBtc()) {
            BitcoinHelper.formatLongBitcoinAmount(amountInSat, bitcoinUnit, locale)
        } else {
            String.format(
                "%s (%s)",
                BitcoinHelper.formatLongBitcoinAmount(amountInSat, bitcoinUnit, locale),
                MoneyHelper.formatLongMonetaryAmount(amountInPrimary, bitcoinUnit, locale)
            )
        }

    /**
     * Get a link to the transaction detail in an external block explorer.
     */
    fun getFormattedTransactionId(context: Context): CharSequence =
        if (operation.hash == null || !HEX_NUMBER.matcher(operation.hash!!).find()) {
            context.getString(R.string.not_available)
        } else {
            linkBuilder.transactionLink(operation.hash)
        }

    /**
     * Get a link to the raw Bitcoin address that received this payment in a block explorer.
     */
    fun getFormattedReceiverAddress(context: Context): CharSequence =
        if (operation.receiverAddress == null) {
            context.getString(R.string.not_available)
        } else {
            linkBuilder.addressLink(operation.receiverAddress)
        }

    /**
     * Get the creation date and time, to be displayed in the operation detail.
     */
    fun getFormattedDateTime(context: Context): String =
        UiUtils.getLongFormattedDate(context, operation.localizedCreationDate)

    /**
     * Get refund message for a failed swap.
     */
    fun getRefundMessage(context: Context, blockchainHeight: Int): String {
        Preconditions.checkNotNull(operation.swap)
        val fundingOutput = operation.swap!!.fundingOutput
        return if (fundingOutput.scriptVersion == Libwallet.AddressVersionSwapsV2.toInt()) {
            context.getString(R.string.operation_swap_expired_desc)
        } else {
            getRefundMessageForSwapV1(context, blockchainHeight)
        }
    }

    /**
     * Get refund message for a failed V1 swap (legacy).
     */
    private fun getRefundMessageForSwapV1(context: Context, blockchainHeight: Int): String {
        checkNotNull(operation.swap)
        checkNotNull(operation.swap!!.fundingOutput.userLockTime)

        val fundingOutput = operation.swap!!.fundingOutput
        val blocksUntilRefund = fundingOutput.userLockTime!! - blockchainHeight
        if (blocksUntilRefund <= 0) { // If userLockTime is reached, swap's already expired/refunded
            return context.getString(R.string.operation_swap_expired_desc)
        }

        val seconds = BlockHelpers.timeInSecsForBlocksWithCertainty(
            blocksUntilRefund,
            TARGET_BLOCK_CONFIRMATION_CERTAINTY
        )
        val hours = seconds / 60 / 60
        return context.getString(R.string.operation_swap_failed_desc, hours, blocksUntilRefund)
    }
}