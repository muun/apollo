package io.muun.apollo.presentation.ui.operation_detail;

import io.muun.apollo.R;
import io.muun.apollo.domain.utils.ExtensionsKt;
import io.muun.apollo.presentation.model.UiOperation;
import io.muun.apollo.presentation.ui.base.BaseActivity;
import io.muun.apollo.presentation.ui.new_operation.TitleAndDescriptionDrawer;
import io.muun.apollo.presentation.ui.utils.StyledStringRes;
import io.muun.apollo.presentation.ui.view.MuunDetailItem;
import io.muun.apollo.presentation.ui.view.MuunHeader;
import io.muun.apollo.presentation.ui.view.MuunHeader.Navigation;
import io.muun.apollo.presentation.ui.view.NoticeBanner;
import io.muun.apollo.presentation.ui.view.ProfilePictureView;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import butterknife.BindView;
import kotlin.Unit;

import javax.validation.constraints.NotNull;

public class OperationDetailActivity extends BaseActivity<OperationDetailPresenter>
        implements OperationDetailView {

    /**
     * Creates an intent to launch this activity.
     */
    public static Intent getStartActivityIntent(@NotNull Context context,
                                                @NotNull Long operationId) {
        final Intent intent = new Intent(context, OperationDetailActivity.class);
        return intent.putExtra(OperationDetailPresenter.OPERATION_ID_KEY, operationId);
    }

    @BindView(R.id.operation_detail_header)
    MuunHeader header;

    @BindView(R.id.operation_detail_profile_picture)
    ProfilePictureView picture;

    @BindView(R.id.operation_detail_title)
    TextView title;

    @BindView(R.id.operation_detail_subtitle)
    TextView subtitle;

    @BindView(R.id.operation_detail_description)
    MuunDetailItem descriptionItem;

    @BindView(R.id.operation_detail_status)
    MuunDetailItem statusItem;

    @BindView(R.id.operation_detail_notice_banner)
    NoticeBanner noticeBanner;

    @BindView(R.id.operation_detail_date)
    MuunDetailItem dateItem;

    @BindView(R.id.operation_detail_amount)
    MuunDetailItem amountItem;

    @BindView(R.id.operation_detail_normal_section)
    ViewGroup normalSection;

    @BindView(R.id.operation_detail_confirmations)
    MuunDetailItem confirmationsItem;

    @BindView(R.id.operation_detail_fee)
    MuunDetailItem feeItem;

    @BindView(R.id.operation_detail_address)
    MuunDetailItem addressItem;

    @BindView(R.id.operation_detail_txid)
    MuunDetailItem transactionIdItem;

    @BindView(R.id.operation_detail_swap_section)
    ViewGroup swapSection;

    @BindView(R.id.operation_detail_swap_invoice)
    MuunDetailItem swapInvoiceItem;

    @BindView(R.id.operation_detail_swap_hash)
    MuunDetailItem swapPaymentHashItem;

    @BindView(R.id.operation_detail_swap_preimage)
    MuunDetailItem swapPreimageItem;

    @BindView(R.id.operation_detail_swap_receiver_pubkey)
    MuunDetailItem swapReceiverPubkeyItem;

    @Override
    protected void inject() {
        getComponent().inject(this);
    }

    @Override
    protected int getLayoutResource() {
        return R.layout.operation_detail_activity;
    }

    @Override
    protected void initializeUi() {
        super.initializeUi();

        header.attachToActivity(this);
        header.setBackgroundColor(Color.TRANSPARENT);
        header.hideTitle();
        header.setNavigation(Navigation.BACK);
    }

    @Override
    public void setOperation(UiOperation operation) {
        final Context context = getViewContext();

        // Header:
        picture.setPictureUri(operation.getPictureUri(this));
        title.setText(operation.getFormattedTitle(context));
        subtitle.setText(operation.getFormattedDisplayAmount(context, false));

        // Description:
        if (operation.getDescription() != null) {
            descriptionItem.setVisibility(View.VISIBLE);
            descriptionItem.setDescription(operation.getDescription());

        } else {
            descriptionItem.setVisibility(View.GONE);
        }

        // Status:
        statusItem.setTitle(operation.getFormattedStatus(context));
        statusItem.setTitleSize(16);
        final CharSequence statusDescription = getStatusDescription(operation);
        if (!TextUtils.isEmpty(statusDescription)) {
            statusItem.setDescription(statusDescription);

        } else {
            statusItem.hideDescription();
        }

        if (operation.isPending()) {
            statusItem.setTitleIcon(R.drawable.ic_clock);

            if (operation.isIncoming() && operation.isRbf()) {
                statusItem.setTitleIconTint(R.color.rbf_color);
            } else {
                statusItem.setTitleIconTint(R.color.pending_color);
            }

        } else {
            statusItem.setTitleIcon(0); // hide it
        }

        if (operation.isPending() && operation.isIncoming() && operation.isRbf()) {
            noticeBanner.setVisibility(View.VISIBLE);
            noticeBanner.setText(
                    new StyledStringRes(this, R.string.rbf_notice_banner).toCharSequence()
            );
            noticeBanner.setOnClickListener(v -> {
                final TitleAndDescriptionDrawer dialog = new TitleAndDescriptionDrawer();
                dialog.setTitle(R.string.rbf_notice_banner_title);
                dialog.setDescription(getString(R.string.rbf_notice_banner_desc));
                showDrawerDialog(dialog);
            });

        } else {
            noticeBanner.setVisibility(View.GONE);
        }

        // Date:
        dateItem.setDescription(operation.getFormattedDateTime(context));

        // Amount:
        amountItem.setDescription(operation.getDetailedAmount());
        amountItem.setOnIconClickListener(
                view -> onCopyAmountToClipboard(operation.getCopyableAmount())
        );

        // Advanced:
        // (Hide everything, restore later)
        normalSection.setVisibility(View.GONE);
        swapSection.setVisibility(View.GONE);
        feeItem.setVisibility(View.GONE);

        if (operation.isSwap()) {
            setSwapOperation(operation);

        } else if (operation.isIncomingSwap()) {
            setIncomingSwapOperation(operation);

        } else {
            setNormalOperation(context, operation);
        }
    }

    private void setSwapOperation(UiOperation operation) {
        // Sections involved:
        swapSection.setVisibility(View.VISIBLE);
        feeItem.setVisibility(View.VISIBLE);

        // On-chain transaction details:
        feeItem.setDescription(operation.getDetailedFee());
        feeItem.setOnIconClickListener(
                view -> onCopyNetworkFeeToClipboard(operation.getCopyableNetworkFee())
        );

        // Lightning network and swap details:
        final String invoice = operation.getSwapInvoice();
        swapInvoiceItem.setDescription(invoice);
        swapInvoiceItem.setOnIconClickListener(view -> onCopyInvoiceToClipboard(invoice));
        swapInvoiceItem.setVisibility(View.VISIBLE);

        swapReceiverPubkeyItem.setVisibility(View.VISIBLE);
        swapReceiverPubkeyItem.setDescription(operation.getSwapReceiverLink());

        // We no longer show fundingTxId to hide the submarine swap impl detail of our ln payments

        final String preimage = operation.getPreimage();
        swapPreimageItem.setDescription(preimage);
        swapPreimageItem.setVisibility(!TextUtils.isEmpty(preimage) ? View.VISIBLE : View.GONE);
        swapPreimageItem.setOnIconClickListener(view -> onCopyPreimageToClipboard(preimage));
    }

    private void setNormalOperation(Context context, UiOperation operation) {
        // Sections involved:
        normalSection.setVisibility(View.VISIBLE);

        // On-chain transaction details:

        if (operation.isIncoming()) {
            feeItem.setVisibility(View.GONE);

        } else {
            feeItem.setVisibility(View.VISIBLE);
            feeItem.setTitle(getString(R.string.operation_detail_fee_outgoing));
            feeItem.setDescription(operation.getDetailedFee());
            feeItem.setOnIconClickListener(
                    view -> onCopyNetworkFeeToClipboard(operation.getCopyableNetworkFee())
            );
        }

        addressItem.setVisibility(View.VISIBLE);
        addressItem.setDescription(operation.getFormattedReceiverAddress(context));
        addressItem.setOnIconClickListener(
                view -> onCopyAddressToClipboard(operation.getReceiverAddress())
        );

        if (!operation.isFailed()) {
            transactionIdItem.setVisibility(View.VISIBLE);
            transactionIdItem.setDescription(operation.getFormattedTransactionId(context));
            transactionIdItem.setOnIconClickListener(
                    view -> onShareTransactionId(operation.getTransactionId())
            );
            transactionIdItem.setOnLongClickListener(view -> {
                onCopyTransactionIdToClipboard(operation.getTransactionId());
                return true; // long click handled
            });

            confirmationsItem.setDescription(operation.getConfirmations());

            if (operation.isIncoming() || operation.isCyclical()) {
                addressItem.setTitle(getString(R.string.operation_detail_address_incoming));
            } else {
                addressItem.setTitle(getString(R.string.operation_detail_address_outgoing));
            }

        } else {
            // If is Tx FAILED, then we don't show these pieces of data
            transactionIdItem.setVisibility(View.GONE);
            confirmationsItem.setVisibility(View.GONE);
        }
    }

    private void setIncomingSwapOperation(final UiOperation operation) {
        // Sections involved:
        swapSection.setVisibility(View.VISIBLE);

        final String paymentHash = operation.getPaymentHash();
        swapPaymentHashItem.setDescription(paymentHash);
        swapPaymentHashItem.setVisibility(
                !TextUtils.isEmpty(paymentHash) ? View.VISIBLE : View.GONE
        );
        swapPaymentHashItem.setOnIconClickListener(view -> onCopyPreimageToClipboard(paymentHash));

        final String preimage = operation.getPreimage();
        swapPreimageItem.setDescription(preimage);
        swapPreimageItem.setVisibility(!TextUtils.isEmpty(preimage) ? View.VISIBLE : View.GONE);
        swapPreimageItem.setOnIconClickListener(view -> onCopyPreimageToClipboard(preimage));

        if (!ExtensionsKt.isEmpty(operation.getInvoiceDescription())) {
            descriptionItem.setVisibility(View.VISIBLE);
            descriptionItem.setDescription(operation.getInvoiceDescription());
        }
    }

    private CharSequence getStatusDescription(UiOperation operation) {

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
    private CharSequence getPendingStatusDescription(UiOperation operation) {

        if (operation.is0ConfSwap()) {
            return getString(R.string.operation_swap_pending_0conf_desc);
        }

        return TextUtils.concat(
                getString(R.string.operation_swap_pending_desc),
                " ",
                new StyledStringRes(this, R.string.why, this::onWhyThisClick).toCharSequence()
        );
    }

    private Unit onWhyThisClick(String linkId) {
        final TitleAndDescriptionDrawer dialog = new TitleAndDescriptionDrawer();
        dialog.setTitle(R.string.operation_swap_pending_confirmations_explanation_title);
        dialog.setDescription(getString(R.string.operation_swap_pending_explanation_desc));
        dialog.show(getSupportFragmentManager(), null);

        presenter.reportShowConfirmationNeededInfo();
        return null;
    }

    private void onCopyInvoiceToClipboard(String invoice) {
        presenter.copyLnInvoiceToClipboard(invoice);
        showTextToast(getString(R.string.operation_detail_invoice_copied));
    }

    private void onCopyPreimageToClipboard(String preimage) {
        presenter.copySwapPreimageToClipboard(preimage);
        showTextToast(getString(R.string.operation_detail_preimage_copied));
    }

    private void onCopyTransactionIdToClipboard(String transactionId) {
        presenter.copyTransactionIdToClipboard(transactionId);
        showTextToast(getString(R.string.operation_detail_txid_copied));
    }

    private void onShareTransactionId(String transactionId) {
        presenter.shareTransactionId(transactionId);
    }

    private void onCopyAmountToClipboard(String amount) {
        presenter.copyAmountToClipboard(amount);
        showTextToast(getString(R.string.operation_detail_amount_copied));
    }

    private void onCopyNetworkFeeToClipboard(String fee) {
        presenter.copyNetworkFeeToClipboard(fee);
        showTextToast(getString(R.string.operation_detail_fee_copied));
    }

    private void onCopyAddressToClipboard(String address) {
        presenter.copyNetworkFeeToClipboard(address);
        showTextToast(getString(R.string.operation_detail_address_copied));
    }
}
