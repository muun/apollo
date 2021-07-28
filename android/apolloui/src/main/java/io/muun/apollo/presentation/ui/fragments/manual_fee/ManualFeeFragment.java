package io.muun.apollo.presentation.ui.fragments.manual_fee;

import io.muun.apollo.R;
import io.muun.apollo.domain.Flags;
import io.muun.apollo.domain.model.CurrencyDisplayMode;
import io.muun.apollo.domain.model.FeeOption;
import io.muun.apollo.domain.model.PaymentAnalysis;
import io.muun.apollo.domain.model.PaymentContext;
import io.muun.apollo.domain.model.PaymentRequest;
import io.muun.apollo.presentation.ui.base.SingleFragment;
import io.muun.apollo.presentation.ui.new_operation.NewOpExtensionsKt;
import io.muun.apollo.presentation.ui.new_operation.PaymentRequestCompanion;
import io.muun.apollo.presentation.ui.new_operation.TitleAndDescriptionDrawer;
import io.muun.apollo.presentation.ui.utils.UiUtils;
import io.muun.apollo.presentation.ui.view.FeeManualInput;
import io.muun.apollo.presentation.ui.view.HtmlTextView;
import io.muun.apollo.presentation.ui.view.MuunButton;
import io.muun.apollo.presentation.ui.view.MuunHeader.Navigation;
import io.muun.apollo.presentation.ui.view.RichText;
import io.muun.apollo.presentation.ui.view.StatusMessage;
import io.muun.common.Rules;
import io.muun.common.model.SizeForAmount;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;
import androidx.annotation.NonNull;
import butterknife.BindString;
import butterknife.BindView;
import butterknife.OnClick;
import icepick.State;

import java.util.List;
import javax.validation.constraints.NotNull;

// TODO refactor, remove unused code and kotlinize
public class ManualFeeFragment extends SingleFragment<ManualFeePresenter> implements ManualFeeView {

    public static ManualFeeFragment create(@NonNull PaymentRequest payReq) {
        final ManualFeeFragment fragment = new ManualFeeFragment();
        fragment.setArguments(NewOpExtensionsKt.toBundle(payReq));
        return fragment;
    }

    public static PaymentRequest getPaymentRequest(@NotNull Bundle bundle) {
        return PaymentRequestCompanion.fromBundle(bundle);
    }

    @BindView(R.id.fee_options_message)
    HtmlTextView message;

    @BindView(R.id.fee_manual_input)
    FeeManualInput feeInput;

    @BindView(R.id.status_message)
    StatusMessage statusMessage;

    @BindView(R.id.use_maximum_fee)
    TextView useMaximumFee;

    @BindView(R.id.confirm_fee)
    MuunButton confirmButton;

    @BindString(R.string.manual_fee_message)
    String messageText;

    @BindString(R.string.manual_fee_how_this_works)
    String howThisWorksText;

    @State
    CurrencyDisplayMode currencyDisplayMode;

    private boolean shouldShowMaxFeeButton;

    @Override
    protected void inject() {
        getComponent().inject(this);
    }

    @Override
    protected int getLayoutResource() {
        return R.layout.manual_fee_selection_fragment;
    }

    @Override
    protected void initializeUi(View view) {
        super.initializeUi(view);

        getParentActivity().getHeader().setNavigation(Navigation.BACK);
        getParentActivity().getHeader().showTitle(R.string.edit_fee_title);

        final CharSequence content = TextUtils.concat(
                messageText,
                ". ",
                new RichText(howThisWorksText).setLink(this::onHowThisWorksClick)
        );

        message.setText(content);
        feeInput.requestFocusInput();

        confirmButton.setEnabled(feeInput.getFeeRate() != null);
    }

    private void onHowThisWorksClick() {
        final TitleAndDescriptionDrawer dialog = new TitleAndDescriptionDrawer();
        dialog.setTitle(R.string.manual_fee_how_this_works_explanation_title);
        dialog.setDescription(getString(R.string.manual_fee_how_this_works_explanation_desc));
        showDrawerDialog(dialog);

        presenter.reportShowManualFeeInfo();
    }

    @Override
    public void setCurrencyDisplayMode(CurrencyDisplayMode currencyDisplayMode) {
        this.currencyDisplayMode = currencyDisplayMode;
    }

    @Override
    public void setPaymentContext(PaymentContext payCtx, PaymentRequest payReq) {

        feeInput.setOnChangeListener(feeRateInSatsPerVByte -> {

            // 1. "Reset" views to initial state, if later analysis decides it, it will change them
            confirmButton.setEnabled(false);
            statusMessage.setVisibility(View.GONE);
            feeInput.resetVisibility();
            useMaximumFee.setVisibility(shouldShowMaxFeeButton ? View.VISIBLE : View.GONE);

            // 1.5 If feeRate is null, we're back at initial state (empty input), nothing else to do
            if (feeRateInSatsPerVByte == null) {
                return;
            }

            final double feeRateInWu = Rules.toSatsPerWeight(feeRateInSatsPerVByte);

            final double minMempoolFeeRateInWu = payCtx.getMinimumFeeRate();
            final double minProtocolFeeRateInWu = Rules.OP_MINIMUM_FEE_RATE;

            final double minFeeRateInWu = Math.max(minMempoolFeeRateInWu, minProtocolFeeRateInWu);

            final PaymentAnalysis analysis;
            try {
                analysis = payCtx.analyze(payReq.withFeeRate(feeRateInWu));

            } catch (Throwable error) {
                presenter.handleError(error);
                return;
            }

            final FeeOption feeOptionSlow = payCtx.getSlowFeeOption();

            // 2. Always show analysis data
            feeInput.setCurrencyDisplayMode(currencyDisplayMode);
            feeInput.setFee(analysis.getFee());
            feeInput.setMaxTimeMs(payCtx.estimateMaxTimeMsFor(feeRateInWu));

            // 3. Warning/Error analysis
            if (feeRateInWu < minFeeRateInWu) {
                final int errorTitle;
                final int errorDesc;

                // This fee rate is too low. Is it because it doesn't match current network
                // requirements, or because it's below the protocol-level minimum?
                if (minMempoolFeeRateInWu > minProtocolFeeRateInWu) {
                    errorTitle = R.string.manual_fee_high_traffic_error_message;
                    errorDesc = R.string.manual_fee_high_traffic_error_desc;
                } else {
                    errorTitle = R.string.manual_fee_too_low_error_message;
                    errorDesc = R.string.manual_fee_too_low_error_desc;
                }

                final String errorArg = UiUtils.formatFeeRate(Rules.toSatsPerVbyte(minFeeRateInWu));

                statusMessage.setError(getString(errorTitle), getString(errorDesc, errorArg));

            } else if (!analysis.getCanPayWithSelectedFee()) {
                statusMessage.setError(
                        R.string.manual_fee_insufficient_funds_message,
                        R.string.manual_fee_insufficient_funds_desc
                );

            }  else if (feeRateInWu > Rules.OP_MAXIMUM_FEE_RATE) {
                final String formattedMaxFeeRate = UiUtils
                        .formatFeeRate(Rules.toSatsPerVbyte(Rules.OP_MAXIMUM_FEE_RATE));

                statusMessage.setError(
                        getString(R.string.manual_fee_too_high_message),
                        getString(R.string.manual_fee_too_high_desc, formattedMaxFeeRate)
                );

            } else if (feeRateInWu < feeOptionSlow.getSatoshisPerByte()) {
                statusMessage.setWarning(
                        R.string.manual_fee_low_warning_message,
                        R.string.manual_fee_low_warning_desc
                );

                confirmButton.setEnabled(true); // just a warning

            } else {
                // All good!
                confirmButton.setEnabled(true);
            }
        });

        shouldShowMaxFeeButton = shouldShowMaximumFeeButton(payCtx, payReq);

        if (shouldShowMaxFeeButton) {

            useMaximumFee.setVisibility(View.VISIBLE);
            useMaximumFee.setOnClickListener(v -> {

                final double maxFeeRateInWu = calculateMaxFeeRate(payCtx, payReq);

                feeInput.setFeeRate(Rules.toSatsPerVbyte(maxFeeRateInWu));
                useMaximumFee.setVisibility(View.GONE);
            });
        }
    }

    private double calculateMaxFeeRate(PaymentContext paymentContext, PaymentRequest payReq) {
        final long amountInSatoshis = paymentContext.convertToSatoshis(payReq.getAmount());

        final List<SizeForAmount> sizeProgression = paymentContext.getNextTransactionSize()
                .sizeProgression;

        final int sizeOfAllFundsTxInWu = sizeProgression.get(sizeProgression.size() - 1)
                .sizeInBytes;

        final long maxFeeInSatoshis = paymentContext.getUserBalance() - amountInSatoshis;

        return maxFeeInSatoshis / (double) sizeOfAllFundsTxInWu;
    }

    private boolean shouldShowMaximumFeeButton(PaymentContext payCtx, PaymentRequest payReq) {
        // TODO disabled until properly tested and QA vetted
        if (!Flags.USE_MAXIMUM_FEE_ENABLED) {
            return false;
        }

        // TODO we could do a lot less counts and checks.
        final FeeOption feeOptionFast = payCtx.getFastFeeOption();
        final FeeOption feeOptionMid = payCtx.getMediumFeeOption();
        final FeeOption feeOptionSlow = payCtx.getSlowFeeOption();

        final boolean canPayWithAll = canPayWithFeeOption(payCtx, payReq, feeOptionFast)
                && canPayWithFeeOption(payCtx, payReq, feeOptionMid)
                && canPayWithFeeOption(payCtx, payReq, feeOptionSlow);

        return !canPayWithAll; // can't pay with at least one fee option
    }

    private boolean canPayWithFeeOption(PaymentContext paymentCtx,
                                        PaymentRequest payReq,
                                        FeeOption feeOption) {

        final PaymentRequest updatedPayReq = payReq.withFeeRate(feeOption.getSatoshisPerByte());

        final PaymentAnalysis analysis;
        try {
            analysis = paymentCtx.analyze(updatedPayReq);

        } catch (Throwable error) {
            presenter.handleError(error);
            return false;
        }

        return analysis.getCanPayWithSelectedFee();
    }

    @OnClick(R.id.confirm_fee)
    void onConfirmFeeClick() {
        presenter.confirmFee(Rules.toSatsPerWeight(feeInput.getFeeRate()));
    }
}
