package io.muun.apollo.presentation.ui.fragments.recommended_fee;


import io.muun.apollo.R;
import io.muun.apollo.domain.model.CurrencyDisplayMode;
import io.muun.apollo.domain.model.FeeOption;
import io.muun.apollo.domain.model.PaymentAnalysis;
import io.muun.apollo.domain.model.PaymentContext;
import io.muun.apollo.domain.model.PaymentRequest;
import io.muun.apollo.presentation.ui.base.SingleFragment;
import io.muun.apollo.presentation.ui.new_operation.TitleAndDescriptionDrawer;
import io.muun.apollo.presentation.ui.view.FeeManualItem;
import io.muun.apollo.presentation.ui.view.FeeOptionItem;
import io.muun.apollo.presentation.ui.view.HtmlTextView;
import io.muun.apollo.presentation.ui.view.MuunButton;
import io.muun.apollo.presentation.ui.view.MuunHeader.Navigation;
import io.muun.apollo.presentation.ui.view.RichText;
import io.muun.apollo.presentation.ui.view.StatusMessage;
import io.muun.common.Rules;
import io.muun.common.utils.Preconditions;

import android.text.TextUtils;
import android.view.View;
import butterknife.BindString;
import butterknife.BindView;
import butterknife.OnClick;
import icepick.State;

public class RecommendedFeeFragment extends SingleFragment<RecommendedFeePresenter>
        implements RecommendedFeeView {

    @BindView(R.id.fee_options_message)
    HtmlTextView message;

    @BindView(R.id.fee_option_fast)
    FeeOptionItem feeOptionItemFast;

    @BindView(R.id.fee_option_medium)
    FeeOptionItem feeOptionItemMedium;

    @BindView(R.id.fee_option_slow)
    FeeOptionItem feeOptionItemSlow;

    @BindView(R.id.enter_fee_manually)
    FeeManualItem feeManualItem;

    @BindView(R.id.status_message)
    StatusMessage statusMessage;

    @BindView(R.id.confirm_fee)
    MuunButton confirmButton;

    @BindString(R.string.fee_options_message)
    String messageText;

    @BindString(R.string.fee_options_whats_this)
    String whatsThisText;

    private Double selectedFeeRate;

    @State
    CurrencyDisplayMode currencyDisplayMode;

    @Override
    protected void inject() {
        getComponent().inject(this);
    }

    @Override
    protected int getLayoutResource() {
        return R.layout.recommended_fee_selection_fragment;
    }

    @Override
    protected void initializeUi(View view) {
        super.initializeUi(view);

        getParentActivity().getHeader().setNavigation(Navigation.EXIT);

        final CharSequence content = TextUtils.concat(
                messageText,
                ". ",
                new RichText(whatsThisText).setLink(this::onWhatsThisClick)
        );

        message.setText(content);

        if (selectedFeeRate == null) {
            confirmButton.setEnabled(false);
        }
    }

    @Override
    public void setCurrencyDisplayMode(CurrencyDisplayMode currencyDisplayMode) {
        this.currencyDisplayMode = currencyDisplayMode;
    }

    @Override
    public void setPaymentContext(PaymentContext payCtx, PaymentRequest payReq) {

        final FeeOption feeOptionFast = payCtx.getFastFeeOption();
        final FeeOption feeOptionMid = payCtx.getMediumFeeOption();
        final FeeOption feeOptionSlow = payCtx.getSlowFeeOption();

        analyzeAndBindFeeOption(feeOptionItemFast, payCtx, payReq, feeOptionFast);
        analyzeAndBindFeeOption(feeOptionItemMedium, payCtx, payReq, feeOptionMid);
        analyzeAndBindFeeOption(feeOptionItemSlow, payCtx, payReq, feeOptionSlow);

        if (payReq.getTakeFeeFromAmount()) {
            statusMessage.setWarning(
                    R.string.use_all_funds_warning_message,
                    R.string.fee_options_use_all_funds_warning_desc,
                    false,
                    ':'
            );
        }

        final double feeRateFast = feeOptionFast.getSatoshisPerByte();
        final double feeRateMid = feeOptionMid.getSatoshisPerByte();
        final double feeRateSlow = feeOptionSlow.getSatoshisPerByte();
        hideDuplicatedFeeRateOptions(feeRateFast, feeRateMid, feeRateSlow);

        final double currentFeeRate = Preconditions.checkNotNull(payReq.getFeeInSatoshisPerByte());

        final PaymentAnalysis analysis;
        try {
            analysis = payCtx.analyze(payReq);

        } catch (Throwable error) {
            presenter.handleError(error);
            return;
        }

        if (alreadySomeOptionSelected()) { // then we probably resuming from background, already set
            return;
        }

        confirmButton.setEnabled(false); // will be enabled later if selected fee is OK

        if (analysis.getCanPayWithSelectedFee()) {
            if (Rules.feeRateEquals(currentFeeRate, feeRateFast)) {
                selectFeeOption(feeOptionFast, feeOptionItemFast);

            } else if (Rules.feeRateEquals(currentFeeRate, feeRateMid)) {
                selectFeeOption(feeOptionMid, feeOptionItemMedium);

            } else if (Rules.feeRateEquals(currentFeeRate, feeRateSlow)) {
                selectFeeOption(feeOptionSlow, feeOptionItemSlow);

            } else {
                showManuallySelectedFee(analysis);
            }
        }
    }

    private boolean alreadySomeOptionSelected() {
        return feeOptionItemFast.isSelected()
                || feeOptionItemMedium.isSelected()
                || feeOptionItemSlow.isSelected()
                || feeManualItem.isSelected();
    }

    private void showManuallySelectedFee(PaymentAnalysis analysis) {
        Preconditions.checkNotNull(analysis);

        if (analysis.getCanPayWithSelectedFee()) {
            confirmButton.setEnabled(true);
            selectedFeeRate = analysis.getPayReq().getFeeInSatoshisPerByte();
        }

        feeManualItem.setCurrencyDisplayMode(currencyDisplayMode);
        feeManualItem.setAmount(analysis.getFee().inInputCurrency);
        feeManualItem.setSelected(true);
    }

    private void analyzeAndBindFeeOption(FeeOptionItem feeOptionItem,
                                         PaymentContext paymentContext,
                                         PaymentRequest payReq,
                                         FeeOption feeOption) {

        final PaymentAnalysis analysis;
        try {
            analysis = paymentContext.analyze(payReq.withFeeRate(feeOption.getSatoshisPerByte()));

        } catch (Throwable error) {
            presenter.handleError(error);
            return;
        }

        bindFeeOptionItem(feeOptionItem, analysis, feeOption);
    }

    private void bindFeeOptionItem(FeeOptionItem feeOptionItem,
                                   PaymentAnalysis analysis,
                                   FeeOption feeOption) {

        feeOptionItem.setCurrencyDisplayMode(currencyDisplayMode);
        feeOptionItem.setMaxTimeMs(feeOption.getMaxTimeMs());
        feeOptionItem.setFeeRate(feeOption.getSatoshisPerByte());
        feeOptionItem.setFee(analysis.getFee());

        if (analysis.getCanPayWithSelectedFee()) {

            feeOptionItem.setOnClickListener(v -> {
                selectFeeOption(feeOption, feeOptionItem);
            });

        } else {
            feeOptionItem.setEnabled(false);
        }
    }

    private void hideDuplicatedFeeRateOptions(double fastRate, double mediumRate, double slowRate) {
        if (Rules.feeRateEquals(mediumRate, slowRate)) {
            feeOptionItemSlow.setVisibility(View.GONE);
        }

        if (Rules.feeRateEquals(fastRate, mediumRate)) {
            feeOptionItemMedium.setVisibility(View.GONE);
        }
    }

    private void selectFeeOption(FeeOption option, FeeOptionItem item) {
        selectedFeeRate = option.getSatoshisPerByte();

        feeOptionItemFast.setSelected(false);
        feeOptionItemMedium.setSelected(false);
        feeOptionItemSlow.setSelected(false);
        feeManualItem.setSelected(false);

        item.setSelected(true);
        confirmButton.setEnabled(true);
    }

    private void onWhatsThisClick() {
        final TitleAndDescriptionDrawer dialog = new TitleAndDescriptionDrawer();
        dialog.setTitle(R.string.fee_options_whats_this_explanation_title);
        dialog.setDescription(getString(R.string.fee_options_whats_this_explanation_desc));
        showDrawerDialog(dialog);

        presenter.reportShowSelectFeeInfo();
    }

    @OnClick(R.id.enter_fee_manually)
    void onEditFeeManuallyClick() {
        presenter.editFeeManually();
    }

    @OnClick(R.id.confirm_fee)
    void onConfirmFeeClick() {
        presenter.confirmFee(selectedFeeRate);
    }
}
