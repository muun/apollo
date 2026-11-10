package io.muun.apollo.presentation.ui.operation_detail;

import io.muun.apollo.R;
import io.muun.apollo.databinding.OperationDetailActivityBinding;
import io.muun.apollo.domain.utils.ExtensionsKt;
import io.muun.apollo.presentation.model.UiOperation;
import io.muun.apollo.presentation.ui.base.BaseActivity;
import io.muun.apollo.presentation.ui.new_operation.TitleAndDescriptionDrawer;
import io.muun.apollo.presentation.ui.utils.StyledStringRes;
import io.muun.apollo.presentation.ui.view.MuunHeader.Navigation;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.viewbinding.ViewBinding;
import kotlin.Unit;
import kotlin.jvm.functions.Function1;

import jakarta.validation.constraints.NotNull;

public class OperationDetailActivity extends BaseActivity<OperationDetailPresenter>
        implements OperationDetailView {

    /**
     * Creates an intent to launch this activity.
     */
    public static Intent getStartActivityIntent(
            @NotNull Context context,
            @NotNull Long operationId
    ) {
        final Intent intent = new Intent(context, OperationDetailActivity.class);
        return intent.putExtra(OperationDetailPresenter.OPERATION_ID_KEY, operationId);
    }

    private OperationDetailActivityBinding binding() {
        return (OperationDetailActivityBinding) getBinding();
    }

    @Override
    protected Function1<LayoutInflater, ViewBinding> bindingInflater() {
        return OperationDetailActivityBinding::inflate;
    }

    @Override
    protected void inject() {
        getComponent().inject(this);
    }

    @Override
    protected void initializeUi() {
        super.initializeUi();

        bindHeader();
    }

    private void bindHeader() {
        binding().operationDetailHeader.attachToActivity(this);
        binding().operationDetailHeader.setBackgroundColor(Color.TRANSPARENT);
        binding().operationDetailHeader.hideTitle();
        binding().operationDetailHeader.setNavigation(Navigation.BACK);
    }

    @Override
    public void setOperation(final UiOperation operation) {
        final Context context = getViewContext();

        // Header:
        binding().operationDetailProfilePicture.setPictureUri(operation.getPictureUri(this));
        binding().operationDetailTitle.setText(operation.getFormattedTitle(context));
        binding().operationDetailSubtitle.setText(operation.getFormattedDisplayAmount(
                context,
                false
        ));

        // Description:
        if (operation.getDescription() != null) {
            binding().operationDetailDescription.setVisibility(View.VISIBLE);
            binding().operationDetailDescription.setDescription(operation.getDescription());
        } else {
            binding().operationDetailDescription.setVisibility(View.GONE);
        }

        // Status:
        binding().operationDetailStatus.setTitle(operation.getFormattedStatus(context));
        binding().operationDetailStatus.setTitleSize(16);
        final CharSequence statusDescription = getStatusDescription(operation);
        if (!TextUtils.isEmpty(statusDescription)) {
            binding().operationDetailStatus.setDescription(statusDescription);
        } else {
            binding().operationDetailStatus.hideDescription();
        }

        if (operation.isPending()) {
            binding().operationDetailStatus.setTitleIcon(R.drawable.ic_clock);

            if (operation.isIncoming() && operation.isRbf()) {
                binding().operationDetailStatus.setTitleIconTint(R.color.rbf_color);
            } else {
                binding().operationDetailStatus.setTitleIconTint(R.color.pending_color);
            }
        } else {
            binding().operationDetailStatus.setTitleIcon(0); // hide it
        }

        if (operation.isPending() && operation.isIncoming() && operation.isRbf()) {
            binding().operationDetailNoticeBanner.setVisibility(View.VISIBLE);
            binding().operationDetailNoticeBanner.setText(
                    new StyledStringRes(this, R.string.rbf_notice_banner).toCharSequence()
            );
            binding().operationDetailNoticeBanner.setOnClickListener(v -> {
                final TitleAndDescriptionDrawer dialog = new TitleAndDescriptionDrawer();
                dialog.setTitle(R.string.rbf_notice_banner_title);
                dialog.setDescription(getString(R.string.rbf_notice_banner_desc));
                showDrawerDialog(dialog);
            });
        } else {
            binding().operationDetailNoticeBanner.setVisibility(View.GONE);
        }

        // Date:
        binding().operationDetailDate.setDescription(operation.getFormattedDateTime(context));

        // Amount:
        binding().operationDetailAmount.setDescription(operation.getDetailedAmount());
        binding().operationDetailAmount.setOnIconClickListener(
                view -> onCopyAmountToClipboard(operation.getCopyableAmount())
        );

        // Advanced:
        // (Hide everything, restore later)
        binding().operationDetailNormalSection.setVisibility(View.GONE);
        binding().operationDetailSwapSection.setVisibility(View.GONE);
        binding().operationDetailFee.setVisibility(View.GONE);

        if (operation.isSwap()) {
            setSwapOperation(operation);
        } else if (operation.isIncomingSwap()) {
            setIncomingSwapOperation(operation);
        } else {
            setNormalOperation(context, operation);
        }
    }

    private void setSwapOperation(final UiOperation operation) {
        // Sections involved:
        binding().operationDetailSwapSection.setVisibility(View.VISIBLE);
        binding().operationDetailFee.setVisibility(View.VISIBLE);

        // On-chain transaction details:
        binding().operationDetailFee.setDescription(operation.getDetailedFee());
        binding().operationDetailFee.setOnIconClickListener(
                view -> onCopyNetworkFeeToClipboard(operation.getCopyableNetworkFee())
        );

        // Lightning network and swap details:
        final String invoice = operation.getSwapInvoice();
        binding().operationDetailSwapInvoice.setDescription(invoice);
        binding().operationDetailSwapInvoice
                .setOnIconClickListener(view -> onCopyInvoiceToClipboard(invoice));
        binding().operationDetailSwapInvoice.setVisibility(View.VISIBLE);

        binding().operationDetailSwapReceiverPubkey.setVisibility(View.VISIBLE);
        binding().operationDetailSwapReceiverPubkey
                .setDescription(operation.getSwapReceiverNodeData());
        final String swapReceiverNodeData = operation.getSwapReceiverNodeData();
        binding().operationDetailSwapReceiverPubkey.setOnIconClickListener(view ->
                onCopyReceivingNodeToClipboard(swapReceiverNodeData)
        );

        // We no longer show fundingTxId to hide the submarine swap impl detail of our ln payments

        final String preimage = operation.getPreimage();
        binding().operationDetailSwapPreimage.setDescription(preimage);
        binding().operationDetailSwapPreimage
                .setVisibility(!TextUtils.isEmpty(preimage) ? View.VISIBLE : View.GONE);
        binding().operationDetailSwapPreimage
                .setOnIconClickListener(view -> onCopyPreimageToClipboard(preimage));
    }

    private void setNormalOperation(final Context context, final UiOperation operation) {
        // Sections involved:
        binding().operationDetailNormalSection.setVisibility(View.VISIBLE);

        // On-chain transaction details:

        if (operation.isIncoming()) {
            binding().operationDetailFee.setVisibility(View.GONE);
        } else {
            binding().operationDetailFee.setVisibility(View.VISIBLE);
            binding().operationDetailFee
                    .setTitle(getString(R.string.operation_detail_fee_outgoing));
            binding().operationDetailFee.setDescription(operation.getDetailedFee());
            binding().operationDetailFee.setOnIconClickListener(
                    view -> onCopyNetworkFeeToClipboard(operation.getCopyableNetworkFee())
            );
        }

        binding().operationDetailAddress.setVisibility(View.VISIBLE);
        binding().operationDetailAddress
                .setDescription(operation.getFormattedReceiverAddress(context));
        binding().operationDetailAddress.setOnIconClickListener(
                view -> onCopyAddressToClipboard(operation.getReceiverAddress())
        );

        if (!operation.isFailed()) {
            binding().operationDetailTxid.setVisibility(View.VISIBLE);
            binding().operationDetailTxid
                    .setDescription(operation.getFormattedTransactionId(context));
            binding().operationDetailTxid.setOnIconClickListener(
                    view -> onShareTransactionId(operation.getTransactionId())
            );
            binding().operationDetailTxid.setOnLongClickListener(view -> {
                onCopyTransactionIdToClipboard(operation.getTransactionId());
                return true; // long click handled
            });

            binding().operationDetailConfirmations.setDescription(operation.getConfirmations());

            if (operation.isIncoming() || operation.isCyclical()) {
                binding().operationDetailAddress
                        .setTitle(getString(R.string.operation_detail_address_incoming));
            } else {
                binding().operationDetailAddress
                        .setTitle(getString(R.string.operation_detail_address_outgoing));
            }
        } else {
            // If is Tx FAILED, then we don't show these pieces of data
            binding().operationDetailTxid.setVisibility(View.GONE);
            binding().operationDetailConfirmations.setVisibility(View.GONE);
        }
    }

    private void setIncomingSwapOperation(final UiOperation operation) {
        // Sections involved:
        binding().operationDetailSwapSection.setVisibility(View.VISIBLE);

        final String paymentHash = operation.getPaymentHash();
        binding().operationDetailSwapHash.setDescription(paymentHash);
        binding().operationDetailSwapHash.setVisibility(
                !TextUtils.isEmpty(paymentHash) ? View.VISIBLE : View.GONE
        );
        binding().operationDetailSwapHash
                .setOnIconClickListener(v -> onCopyPaymentHashToClipboard(paymentHash));

        final String preimage = operation.getPreimage();
        if (operation.isCompleted() && !TextUtils.isEmpty(preimage)) {
            binding().operationDetailSwapPreimage.setDescription(preimage);
            binding().operationDetailSwapPreimage.setVisibility(View.VISIBLE);
            binding().operationDetailSwapPreimage
                    .setOnIconClickListener(view -> onCopyPreimageToClipboard(preimage));
        } else {
            binding().operationDetailSwapPreimage.setVisibility(View.GONE);
        }

        if (!ExtensionsKt.isEmpty(operation.getInvoiceDescription())) {
            binding().operationDetailDescription.setVisibility(View.VISIBLE);
            binding().operationDetailDescription.setDescription(operation.getInvoiceDescription());
        }
    }

    private CharSequence getStatusDescription(final UiOperation operation) {

        switch (operation.getOperationStatus()) {

            case SWAP_PENDING:
            case SWAP_ROUTING:
                return getPendingStatusDescription(operation);

            case SWAP_FAILED:
                return operation.getRefundMessage(this, presenter.getBlockchainHeight());

            case SWAP_EXPIRED:
                return getString(R.string.operation_swap_expired_desc);

            default:
                return "";
        }
    }

    @NonNull
    private CharSequence getPendingStatusDescription(final UiOperation operation) {

        if (operation.is0ConfSwap()) {
            return getString(R.string.operation_swap_pending_0conf_desc);
        }

        return TextUtils.concat(
                getString(R.string.operation_swap_pending_desc),
                " ",
                new StyledStringRes(this, R.string.why, this::onWhyThisClick).toCharSequence()
        );
    }

    private Unit onWhyThisClick(final String linkId) {
        final TitleAndDescriptionDrawer dialog = new TitleAndDescriptionDrawer();
        dialog.setTitle(R.string.operation_swap_pending_confirmations_explanation_title);
        dialog.setDescription(getString(R.string.operation_swap_pending_explanation_desc));
        dialog.show(getSupportFragmentManager(), null);

        presenter.reportShowConfirmationNeededInfo();
        return null;
    }

    private void onCopyInvoiceToClipboard(final String invoice) {
        presenter.copyLnInvoiceToClipboard(invoice);
        showTextToast(getString(R.string.operation_detail_invoice_copied));
    }

    private void onCopyPreimageToClipboard(final String preimage) {
        presenter.copySwapPreimageToClipboard(preimage);
        showTextToast(getString(R.string.operation_detail_preimage_copied));
    }

    private void onCopyPaymentHashToClipboard(final String paymentHash) {
        presenter.copyPaymentHashToClipboard(paymentHash);
        showTextToast(getString(R.string.operation_detail_payment_hash_copied));
    }

    private void onCopyTransactionIdToClipboard(final String transactionId) {
        presenter.copyTransactionIdToClipboard(transactionId);
        showTextToast(getString(R.string.operation_detail_txid_copied));
    }

    private void onShareTransactionId(final String transactionId) {
        presenter.shareTransactionId(transactionId);
    }

    private void onCopyAmountToClipboard(final String amount) {
        presenter.copyAmountToClipboard(amount);
        showTextToast(getString(R.string.operation_detail_amount_copied));
    }

    private void onCopyNetworkFeeToClipboard(final String fee) {
        presenter.copyNetworkFeeToClipboard(fee);
        showTextToast(getString(R.string.operation_detail_fee_copied));
    }

    private void onCopyAddressToClipboard(final String address) {
        presenter.copyNetworkFeeToClipboard(address);
        showTextToast(getString(R.string.operation_detail_address_copied));
    }

    private void onCopyReceivingNodeToClipboard(final String receivingNode) {
        presenter.copyReceivingNodeToClipboard(receivingNode);
        showTextToast(getString(R.string.operation_detail_node_copied));
    }
}
